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

    /** Bounded pool for streamed answers (Bulkhead): long answers cannot take the threads that serve the API. */
    private static final java.util.concurrent.ExecutorService STREAMS = java.util.concurrent.Executors.newFixedThreadPool(8,
        Thread.ofPlatform().name("chat-stream-", 0).daemon(true).factory());

    /**
     * Same as {@code POST /chat} but answers with Server-Sent Events: {@code delta} events with fragments of the text as
     * the model writes it, then {@code done} with the complete response (verified citations, conversation) or
     * {@code error}. The user sees the answer appear in about a second instead of waiting for all of it.
     */
    @PostMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        Long userId = CurrentUser.id();
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(120_000L);
        // The worker thread keeps the user's security context, so the question is stored in the user's current session.
        STREAMS.execute(new org.springframework.security.concurrent.DelegatingSecurityContextRunnable(() -> {
            try {
                ChatResponse response = service.ask(userId, request.question(), request.conversationId(), delta -> {
                    try {
                        if (delta == com.aquasense.intelligence.ai.AiTextProvider.RESTART) {
                            // The model stopped half way and another one starts over: the page clears the text.
                            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("restart")
                                .data(Map.of("reason", "El primer modelo se detuvo; otro escribe la respuesta."),
                                    org.springframework.http.MediaType.APPLICATION_JSON));
                            return;
                        }
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("delta")
                            .data(Map.of("t", delta), org.springframework.http.MediaType.APPLICATION_JSON));
                    } catch (java.io.IOException ex) {
                        throw new java.io.UncheckedIOException(ex);
                    }
                });
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("done")
                    .data(response, org.springframework.http.MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (java.io.UncheckedIOException | java.io.IOException ex) {
                emitter.completeWithError(ex);
            } catch (RuntimeException ex) {
                int status = ex instanceof com.aquasense.common.BusinessException b ? b.getStatus().value()
                    : ex instanceof com.aquasense.intelligence.ai.AiUnavailableException ? 503 : 500;
                String message = status == 500 ? "Ocurrió un error inesperado. Intenta de nuevo." : ex.getMessage();
                try {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("error")
                        .data(Map.of("status", status, "message", message), org.springframework.http.MediaType.APPLICATION_JSON));
                    emitter.complete();
                } catch (java.io.IOException io) {
                    emitter.completeWithError(io);
                }
            }
        }));
        return emitter;
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
