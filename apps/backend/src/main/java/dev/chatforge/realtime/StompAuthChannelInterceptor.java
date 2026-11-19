package dev.chatforge.realtime;

import dev.chatforge.chat.ChatService;
import dev.chatforge.chat.NotFoundException;
import dev.chatforge.security.JwtService;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP sessions and authorizes subscriptions.
 *
 * <ul>
 *   <li>CONNECT must carry {@code Authorization: Bearer <jwt>}; otherwise the connection is refused.</li>
 *   <li>SUBSCRIBE is allowed only to {@code /topic/conversations/{id}} for a conversation the user may
 *       access, and to the user's own {@code /user/queue/errors}.</li>
 *   <li>SEND requires an authenticated session. Access to the target conversation is checked by
 *       {@link ChatService}.</li>
 * </ul>
 *
 * Throwing a {@link MessagingException} makes Spring send a STOMP ERROR frame and close the session.
 * Note: a session stays authorized after its token expires, until the client disconnects.
 */
@Component
class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";
    private static final String USER_ERRORS = "/user/queue/errors";
    private static final Pattern CONVERSATION_TOPIC =
            Pattern.compile("^" + Pattern.quote(ChatService.TOPIC_PREFIX) + "([0-9a-fA-F-]{36})$");

    private final JwtService jwtService;
    private final ChatService chatService;

    /**
     * {@code @Lazy} breaks a bean cycle: this interceptor is part of the inbound channel that
     * SimpMessagingTemplate (needed by ChatService) is built from, so ChatService must not be resolved
     * while the channel is still being configured.
     */
    StompAuthChannelInterceptor(JwtService jwtService, @Lazy ChatService chatService) {
        this.jwtService = jwtService;
        this.chatService = chatService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> requireUser(accessor);
            default -> { }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessagingException("unauthorized");
        }
        var user = jwtService.parse(header.substring(BEARER.length()).trim())
                .orElseThrow(() -> new MessagingException("unauthorized"));
        accessor.setUser(new ChatPrincipal(user));
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        ChatPrincipal principal = requireUser(accessor);
        String destination = accessor.getDestination();
        if (USER_ERRORS.equals(destination)) {
            return;
        }
        Matcher matcher = destination == null ? null : CONVERSATION_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            throw new MessagingException("forbidden");
        }
        try {
            chatService.getConversation(UUID.fromString(matcher.group(1)), principal.user());
        } catch (NotFoundException | IllegalArgumentException e) {
            throw new MessagingException("forbidden");
        }
    }

    private ChatPrincipal requireUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof ChatPrincipal principal) {
            return principal;
        }
        throw new MessagingException("unauthorized");
    }
}
