package dev.chatforge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired AgentRepository agents;
    @Autowired VisitorRepository visitors;
    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;

    @Test
    void findsAgentByEmail() {
        agents.saveAndFlush(new Agent("agent@example.com", "hash", "Agent One"));

        assertThat(agents.findByEmail("agent@example.com")).isPresent();
        assertThat(agents.findByEmail("missing@example.com")).isEmpty();
    }

    @Test
    void rejectsDuplicateAgentEmail() {
        agents.saveAndFlush(new Agent("dup@example.com", "hash", "A"));

        assertThatThrownBy(() -> agents.saveAndFlush(new Agent("dup@example.com", "hash", "B")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void returnsMessagesOfAConversationInChronologicalOrder() {
        Visitor visitor = visitors.save(new Visitor("Visitor"));
        Conversation conversation = conversations.save(new Conversation(visitor));
        Conversation other = conversations.save(new Conversation(visitor));

        messages.save(new Message(conversation, SenderType.VISITOR, visitor.getId(), "first"));
        messages.save(new Message(conversation, SenderType.VISITOR, visitor.getId(), "second"));
        messages.save(new Message(other, SenderType.VISITOR, visitor.getId(), "other conversation"));
        messages.flush();

        var page = messages.findByConversationIdOrderByCreatedAtAsc(conversation.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Message::getContent).containsExactly("first", "second");
    }

    @Test
    void listsConversationsByVisitorAndStatus() {
        Visitor visitor = visitors.save(new Visitor(null));
        Conversation open = conversations.save(new Conversation(visitor));
        Conversation closed = conversations.save(new Conversation(visitor));
        closed.close();
        conversations.flush();

        assertThat(conversations.findByVisitorId(visitor.getId())).hasSize(2);
        assertThat(conversations.findByStatusOrderByUpdatedAtDesc(ConversationStatus.OPEN, PageRequest.of(0, 10))
                .getContent())
                .extracting(Conversation::getId)
                .contains(open.getId())
                .doesNotContain(closed.getId());
    }

    @Test
    void rejectsBlankMessageContent() {
        Visitor visitor = visitors.save(new Visitor("V"));
        Conversation conversation = conversations.save(new Conversation(visitor));

        assertThatThrownBy(() -> messages.saveAndFlush(
                new Message(conversation, SenderType.VISITOR, visitor.getId(), "   ")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
