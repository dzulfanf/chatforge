package dev.chatforge.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, updatable = false)
    private SenderType senderType;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Message() {
    }

    public Message(Conversation conversation, SenderType senderType, UUID senderId, String content) {
        this.conversation = conversation;
        this.senderType = senderType;
        this.senderId = senderId;
        this.content = content;
    }

    public UUID getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public SenderType getSenderType() { return senderType; }
    public UUID getSenderId() { return senderId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
