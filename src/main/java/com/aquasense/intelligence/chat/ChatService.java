package com.aquasense.intelligence.chat;

import com.aquasense.common.BusinessException;
import com.aquasense.intelligence.IntelligenceFacade;
import com.aquasense.intelligence.dto.ChatResponse;
import com.aquasense.knowledge.Citation;
import com.aquasense.knowledge.GuidelineRetriever;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assistant with memory: stores every conversation and sends the recent turns to the AI, so the user can
 * ask follow-up questions ("is that serious?") without repeating the context. Conversations are private and can be deleted.
 */
@Service
public class ChatService {

    static final int MAX_CONVERSATIONS = 50;
    private static final int TITLE_LENGTH = 60;

    private final IntelligenceFacade facade;
    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final GuidelineRetriever guidelines;

    public ChatService(IntelligenceFacade facade, ChatConversationRepository conversations, ChatMessageRepository messages,
                       GuidelineRetriever guidelines) {
        this.facade = facade;
        this.conversations = conversations;
        this.messages = messages;
        this.guidelines = guidelines;
    }

    @Transactional
    public ChatResponse ask(Long userId, String question, Long conversationId) {
        return ask(userId, question, conversationId, null);
    }

    /** With {@code onDelta}, the answer reaches the user fragment by fragment; it is stored complete at the end. */
    @Transactional
    public ChatResponse ask(Long userId, String question, Long conversationId, java.util.function.Consumer<String> onDelta) {
        String text = question.trim();
        ChatConversation conversation = conversationId == null ? null : owned(userId, conversationId);
        List<ChatMessage> previous = conversation == null ? List.of()
            : messages.findByConversationIdOrderByIdAsc(conversation.getId());
        ChatMemory.Window window = ChatMemory.select(previous);
        Set<String> cited = new HashSet<>();
        previous.forEach(m -> cited.addAll(m.citationIds()));
        IntelligenceFacade.ChatAnswer answer = facade.chat(userId, text, window.turns(), cited, window.omitted(), onDelta);
        if (conversation == null) {
            conversation = conversations.save(new ChatConversation(userId, title(text)));
            prune(userId);
        }
        conversation.touch();
        messages.save(new ChatMessage(conversation.getId(), ChatMessage.Role.USER, text, null, List.of()));
        messages.save(new ChatMessage(conversation.getId(), ChatMessage.Role.ASSISTANT, answer.text(), answer.model(),
            answer.citations().stream().map(Citation::id).toList()));
        return new ChatResponse(answer.text(), answer.model(), answer.citations(), answer.removedReferences(),
            conversation.getId(), conversation.getTitle(), window.turns().size(), window.omitted());
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(Long userId) {
        return conversations.findByUserIdOrderByUpdatedAtDesc(userId).stream()
            .map(c -> new ConversationSummary(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt(),
                messages.countByConversationId(c.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(Long userId, Long id) {
        ChatConversation c = owned(userId, id);
        List<MessageView> views = messages.findByConversationIdOrderByIdAsc(id).stream()
            .map(m -> new MessageView(m.getId(), m.getRole().name(), m.getContent(), m.getModel(),
                guidelines.resolve(m.citationIds()), m.getCreatedAt()))
            .toList();
        return new ConversationDetail(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt(), views);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        conversations.delete(owned(userId, id));
    }

    @Transactional
    public int deleteAll(Long userId) {
        List<ChatConversation> all = conversations.findByUserIdOrderByUpdatedAtDesc(userId);
        conversations.deleteAll(all);
        return all.size();
    }

    private void prune(Long userId) {
        List<ChatConversation> all = conversations.findByUserIdOrderByUpdatedAtDesc(userId);
        if (all.size() > MAX_CONVERSATIONS) {
            conversations.deleteAll(all.subList(MAX_CONVERSATIONS, all.size()));
        }
    }

    private ChatConversation owned(Long userId, Long id) {
        return conversations.findByIdAndUserId(id, userId)
            .orElseThrow(() -> BusinessException.notFound("La conversación no existe."));
    }

    static String title(String question) {
        String clean = question.replaceAll("\\s+", " ").trim();
        return clean.length() <= TITLE_LENGTH ? clean : clean.substring(0, TITLE_LENGTH - 1).trim() + "…";
    }

    public record ConversationSummary(Long id, String title, Instant createdAt, Instant updatedAt, long messageCount) {
    }

    public record ConversationDetail(Long id, String title, Instant createdAt, Instant updatedAt, List<MessageView> messages) {
    }

    public record MessageView(Long id, String role, String content, String model, List<Citation> citations, Instant createdAt) {
    }
}
