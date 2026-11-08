package com.aquasense.intelligence.chat;

import com.aquasense.intelligence.dto.ChatRequest;
import com.aquasense.intelligence.dto.ChatResponse;
import com.aquasense.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/intelligence/chat")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        return service.ask(CurrentUser.id(), request.question(), request.conversationId());
    }

    @GetMapping("/conversations")
    public List<ChatService.ConversationSummary> list() {
        return service.list(CurrentUser.id());
    }

    @GetMapping("/conversations/{id}")
    public ChatService.ConversationDetail get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/conversations")
    public Map<String, Integer> deleteAll() {
        return Map.of("deleted", service.deleteAll(CurrentUser.id()));
    }
}
