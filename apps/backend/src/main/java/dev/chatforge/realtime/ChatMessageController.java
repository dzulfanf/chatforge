package dev.chatforge.realtime;

import dev.chatforge.chat.ChatService;
import dev.chatforge.chat.ConflictException;
import dev.chatforge.chat.NotFoundException;
import dev.chatforge.chat.SendMessageRequest;
import jakarta.validation.Validator;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * Receives chat messages sent over STOMP. {@link ChatService} saves the message and broadcasts it to
 * {@code /topic/conversations/{id}} after the transaction commits. Failures go back only to the sender,
 * on {@code /user/queue/errors}.
 */
@Controller
class ChatMessageController {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageController.class);

    private final ChatService chatService;
    private final Validator validator;

    ChatMessageController(ChatService chatService, Validator validator) {
        this.chatService = chatService;
        this.validator = validator;
    }

    @MessageMapping("/conversations/{id}/send")
    void send(@DestinationVariable UUID id, @Payload SendMessageRequest request, Principal principal) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new IllegalArgumentException("invalid_message");
        }
        chatService.sendMessage(id, ((ChatPrincipal) principal).user(), request.content());
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    Map<String, String> handleError(Exception e) {
        if (e instanceof NotFoundException) {
            return Map.of("error", "not_found");
        }
        if (e instanceof ConflictException) {
            return Map.of("error", e.getMessage());
        }
        if (e instanceof IllegalArgumentException) {
            return Map.of("error", "invalid_message");
        }
        log.error("Unexpected error handling STOMP message", e);
        return Map.of("error", "internal_error");
    }
}
