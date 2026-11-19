package dev.chatforge.chat;

import dev.chatforge.domain.Agent;
import dev.chatforge.domain.AgentRepository;
import dev.chatforge.domain.Conversation;
import dev.chatforge.domain.ConversationRepository;
import dev.chatforge.domain.ConversationStatus;
import dev.chatforge.domain.Message;
import dev.chatforge.domain.MessageRepository;
import dev.chatforge.domain.SenderType;
import dev.chatforge.domain.Visitor;
import dev.chatforge.domain.VisitorRepository;
import dev.chatforge.security.AuthenticatedUser;
import dev.chatforge.security.Role;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversation and message use cases. Access rules live here (not in the controllers) so that other
 * entry points, such as STOMP messaging, can reuse them.
 */
@Service
public class ChatService {

    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final VisitorRepository visitors;
    private final AgentRepository agents;

    public ChatService(
            ConversationRepository conversations,
            MessageRepository messages,
            VisitorRepository visitors,
            AgentRepository agents) {
        this.conversations = conversations;
        this.messages = messages;
        this.visitors = visitors;
        this.agents = agents;
    }

    public record OpenResult(ConversationResponse conversation, boolean created) {
    }

    /**
     * Returns the visitor's open conversation, creating one if none exists. Not transactional on purpose:
     * if two requests race, the database's unique index rejects the second insert and we fall back to
     * the conversation that won.
     */
    public OpenResult openConversation(UUID visitorId) {
        Visitor visitor = visitors.findById(visitorId).orElseThrow(NotFoundException::new);

        var existing = conversations.findFirstByVisitorIdAndStatus(visitorId, ConversationStatus.OPEN);
        if (existing.isPresent()) {
            return new OpenResult(ConversationResponse.from(existing.get()), false);
        }
        try {
            Conversation created = conversations.saveAndFlush(new Conversation(visitor));
            return new OpenResult(ConversationResponse.from(created), true);
        } catch (DataIntegrityViolationException e) {
            return conversations.findFirstByVisitorIdAndStatus(visitorId, ConversationStatus.OPEN)
                    .map(c -> new OpenResult(ConversationResponse.from(c), false))
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID conversationId, AuthenticatedUser user) {
        return ConversationResponse.from(getAccessible(conversationId, user));
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> listConversations(ConversationStatus status, int page, int size) {
        return PageResponse.from(conversations
                .findByStatusOrderByUpdatedAtDesc(status, pageRequest(page, size))
                .map(ConversationResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> listMessages(UUID conversationId, AuthenticatedUser user, int page, int size) {
        getAccessible(conversationId, user);
        return PageResponse.from(messages
                .findByConversationIdOrderByCreatedAtAsc(conversationId, pageRequest(page, size))
                .map(MessageResponse::from));
    }

    @Transactional
    public MessageResponse sendMessage(UUID conversationId, AuthenticatedUser user, String content) {
        Conversation conversation = getAccessible(conversationId, user);
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new ConflictException("conversation_closed");
        }
        SenderType senderType = user.role() == Role.AGENT ? SenderType.AGENT : SenderType.VISITOR;
        Message message = messages.save(new Message(conversation, senderType, user.id(), content));
        conversation.touch();
        return MessageResponse.from(message);
    }

    @Transactional
    public ConversationResponse assign(UUID conversationId, UUID agentId) {
        Conversation conversation = conversations.findById(conversationId).orElseThrow(NotFoundException::new);
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new ConflictException("conversation_closed");
        }
        Agent agent = agents.findById(agentId).orElseThrow(NotFoundException::new);
        conversation.assignTo(agent);
        return ConversationResponse.from(conversation);
    }

    @Transactional
    public ConversationResponse close(UUID conversationId) {
        Conversation conversation = conversations.findById(conversationId).orElseThrow(NotFoundException::new);
        if (conversation.getStatus() != ConversationStatus.CLOSED) {
            conversation.close();
        }
        return ConversationResponse.from(conversation);
    }

    /** Agents can access any conversation; a visitor only their own (others get "not found"). */
    private Conversation getAccessible(UUID conversationId, AuthenticatedUser user) {
        Conversation conversation = conversations.findById(conversationId).orElseThrow(NotFoundException::new);
        if (user.role() == Role.VISITOR && !conversation.getVisitorId().equals(user.id())) {
            throw new NotFoundException();
        }
        return conversation;
    }

    private static PageRequest pageRequest(int page, int size) {
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize);
    }
}
