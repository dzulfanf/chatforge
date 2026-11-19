package dev.chatforge.chat;

import dev.chatforge.domain.Conversation;
import dev.chatforge.domain.ConversationStatus;
import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID visitorId,
        UUID assignedAgentId,
        ConversationStatus status,
        Instant createdAt,
        Instant updatedAt) {

    static ConversationResponse from(Conversation c) {
        return new ConversationResponse(
                c.getId(), c.getVisitorId(), c.getAssignedAgentId(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
