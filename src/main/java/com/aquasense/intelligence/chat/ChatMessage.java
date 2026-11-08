package com.aquasense.intelligence.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    public enum Role { USER, ASSISTANT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id")
    private Long conversationId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Role role;

    private String content;
    private String model;

    /** Ids de fragmentos normativos validados, separados por comas. */
    private String citations;

    @Column(name = "created_at")
    private Instant createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(Long conversationId, Role role, String content, String model, List<String> citationIds) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content.length() > 6000 ? content.substring(0, 6000) : content;
        this.model = model;
        String ids = citationIds == null ? "" : String.join(",", citationIds);
        this.citations = ids.isEmpty() ? null : ids.length() > 400 ? ids.substring(0, ids.lastIndexOf(',', 400)) : ids;
        this.createdAt = Instant.now();
    }

    public List<String> citationIds() {
        return citations == null || citations.isBlank() ? List.of() : Arrays.asList(citations.split(","));
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public String getModel() { return model; }
    public Instant getCreatedAt() { return createdAt; }
}
