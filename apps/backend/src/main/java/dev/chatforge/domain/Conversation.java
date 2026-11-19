package dev.chatforge.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visitor_id", nullable = false, updatable = false)
    private Visitor visitor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    private Agent assignedAgent;

    // Read-only views of the foreign keys, so ids can be read without loading the lazy associations.
    @Column(name = "visitor_id", insertable = false, updatable = false)
    private UUID visitorId;

    @Column(name = "assigned_agent_id", insertable = false, updatable = false)
    private UUID assignedAgentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationStatus status = ConversationStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Conversation() {
    }

    public Conversation(Visitor visitor) {
        this.visitor = visitor;
        this.visitorId = visitor.getId();
    }

    public void assignTo(Agent agent) {
        this.assignedAgent = agent;
        this.assignedAgentId = agent.getId();
        this.updatedAt = Instant.now();
    }

    public void close() {
        this.status = ConversationStatus.CLOSED;
        this.updatedAt = Instant.now();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Visitor getVisitor() { return visitor; }
    public UUID getVisitorId() { return visitorId; }
    public Agent getAssignedAgent() { return assignedAgent; }
    public UUID getAssignedAgentId() { return assignedAgentId; }
    public ConversationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
