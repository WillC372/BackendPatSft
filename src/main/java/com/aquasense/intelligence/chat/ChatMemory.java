package com.aquasense.intelligence.chat;

import com.aquasense.intelligence.ai.AiPrompt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Memory window of the assistant: takes the most recent messages that fit in the budget (count and characters),
 * always starting with a user message so the turns stay properly alternated.
 */
public final class ChatMemory {

    static final int MAX_MESSAGES = 12;
    static final int MAX_CHARS = 9000;
    static final int MAX_MESSAGE_CHARS = 1500;

    private ChatMemory() {
    }

    public static Window select(List<ChatMessage> messages) {
        List<AiPrompt.Turn> turns = new ArrayList<>();
        int chars = 0;
        int index = messages.size() - 1;
        for (; index >= 0 && turns.size() < MAX_MESSAGES; index--) {
            ChatMessage m = messages.get(index);
            String content = m.getContent().length() > MAX_MESSAGE_CHARS
                ? m.getContent().substring(0, MAX_MESSAGE_CHARS) + "…" : m.getContent();
            if (chars + content.length() > MAX_CHARS) {
                break;
            }
            chars += content.length();
            turns.add(new AiPrompt.Turn(m.getRole() == ChatMessage.Role.ASSISTANT, content));
        }
        Collections.reverse(turns);
        while (!turns.isEmpty() && turns.get(0).assistant()) {
            turns.remove(0);
        }
        return new Window(turns, messages.size() - turns.size());
    }

    public record Window(List<AiPrompt.Turn> turns, int omitted) {
    }
}
