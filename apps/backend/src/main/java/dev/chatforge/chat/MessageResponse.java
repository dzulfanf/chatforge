package dev.chatforge.chat;

import dev.chatforge.domain.Message;
import dev.chatforge.domain.SenderType;
import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        SenderType senderType,
        UUID senderId,
        String content,
        Instant createdAt) {

    static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(), m.getConversationId(), m.getSenderType(), m.getSenderId(), m.getContent(), m.getCreatedAt());
    }
}
