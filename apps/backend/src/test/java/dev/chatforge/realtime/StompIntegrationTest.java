package dev.chatforge.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import dev.chatforge.TestcontainersConfiguration;
import dev.chatforge.chat.ChatService;
import dev.chatforge.domain.Agent;
import dev.chatforge.domain.AgentRepository;
import dev.chatforge.domain.Visitor;
import dev.chatforge.domain.VisitorRepository;
import dev.chatforge.security.JwtService;
import dev.chatforge.security.Role;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class StompIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final long WAIT_SECONDS = 5;

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired ChatService chatService;
    @Autowired AgentRepository agents;
    @Autowired VisitorRepository visitors;

    private final ThreadPoolTaskScheduler scheduler = createScheduler();
    private final WebSocketStompClient stompClient = createClient(scheduler);

    private static ThreadPoolTaskScheduler createScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    private static WebSocketStompClient createClient(ThreadPoolTaskScheduler scheduler) {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        client.setTaskScheduler(scheduler); // needed to track subscription receipts
        client.setDefaultHeartbeat(new long[] {0, 0}); // no heartbeats
        return client;
    }

    @AfterEach
    void stopClient() {
        stompClient.stop();
        scheduler.shutdown();
    }

    // --- fixtures ---

    private record Party(UUID id, String token) {
    }

    private Party newVisitor() {
        Visitor visitor = visitors.save(new Visitor("visitor"));
        return new Party(visitor.getId(), jwtService.issue(visitor.getId(), Role.VISITOR, Duration.ofMinutes(5)));
    }

    private Party newAgent() {
        Agent agent = agents.save(new Agent("agent-" + UUID.randomUUID() + "@example.com", "hash", "Agent"));
        return new Party(agent.getId(), jwtService.issue(agent.getId(), Role.AGENT, Duration.ofMinutes(5)));
    }

    private UUID openConversation(Party visitor) {
        return chatService.openConversation(visitor.id()).conversation().id();
    }

    // --- STOMP helpers ---

    /** Connection events: "connected", "error" (ERROR frame or transport failure). */
    private record Connection(StompSession session, BlockingQueue<String> events) {
    }

    private Connection connect(String token, String origin) throws Exception {
        BlockingQueue<String> events = new LinkedBlockingQueue<>();
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin(origin);
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        var handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                events.add("connected");
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add("error");
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                events.add("error");
            }
        };
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", handshake, connectHeaders, handler)
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
        return new Connection(session, events);
    }

    private Connection connect(Party party) throws Exception {
        Connection connection = connect(party.token(), ALLOWED_ORIGIN);
        assertThat(connection.events().poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("connected");
        return connection;
    }

    private BlockingQueue<JsonNode> subscribe(StompSession session, String destination) throws Exception {
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        CountDownLatch subscribed = new CountDownLatch(1);
        session.setAutoReceipt(true);
        var subscription = session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((JsonNode) payload);
            }
        });
        subscription.addReceiptTask(subscribed::countDown);
        assertThat(subscribed.await(WAIT_SECONDS, TimeUnit.SECONDS)).as("subscription receipt").isTrue();
        return received;
    }

    private static String topic(UUID conversationId) {
        return ChatService.TOPIC_PREFIX + conversationId;
    }

    private static void sendOverStomp(StompSession session, UUID conversationId, String content) {
        session.send("/app/conversations/" + conversationId + "/send", Map.of("content", content));
    }

    private static JsonNode next(BlockingQueue<JsonNode> queue) throws InterruptedException {
        JsonNode node = queue.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(node).as("expected a frame within %ds", WAIT_SECONDS).isNotNull();
        return node;
    }

    private static void assertNothingReceived(BlockingQueue<JsonNode> queue) throws InterruptedException {
        assertThat(queue.poll(1, TimeUnit.SECONDS)).isNull();
    }

    // --- connecting ---

    @Test
    void connectWithoutTokenIsRejected() throws Exception {
        assertRejected(null, ALLOWED_ORIGIN);
    }

    @Test
    void connectWithInvalidTokenIsRejected() throws Exception {
        assertRejected("not-a-jwt", ALLOWED_ORIGIN);
    }

    @Test
    void connectWithExpiredTokenIsRejected() throws Exception {
        String expired = jwtService.issue(UUID.randomUUID(), Role.AGENT, Duration.ofSeconds(-60));

        assertRejected(expired, ALLOWED_ORIGIN);
    }

    @Test
    void handshakeFromDisallowedOriginIsRejected() {
        Party agent = newAgent();

        assertThatThrownBy(() -> connect(agent.token(), "http://evil.example")).isInstanceOf(Exception.class);
    }

    /** A rejected CONNECT yields an ERROR frame (or a failed connection) and never "connected". */
    private void assertRejected(String token, String origin) throws Exception {
        BlockingQueue<String> events;
        try {
            events = connect(token, origin).events();
        } catch (Exception e) {
            return; // the connection failed outright, which is also a rejection
        }
        assertThat(events.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("error");
    }

    // --- messaging ---

    @Test
    void messageSentOverStompIsPersistedAndBroadcastToSubscribers() throws Exception {
        Party visitor = newVisitor();
        Party agent = newAgent();
        UUID conversationId = openConversation(visitor);
        Connection visitorConnection = connect(visitor);
        Connection agentConnection = connect(agent);
        var visitorInbox = subscribe(visitorConnection.session(), topic(conversationId));
        var agentInbox = subscribe(agentConnection.session(), topic(conversationId));

        sendOverStomp(visitorConnection.session(), conversationId, "hello over stomp");

        for (BlockingQueue<JsonNode> inbox : List.of(visitorInbox, agentInbox)) {
            JsonNode message = next(inbox);
            assertThat(message.get("content").asText()).isEqualTo("hello over stomp");
            assertThat(message.get("senderType").asText()).isEqualTo("VISITOR");
            assertThat(message.get("senderId").asText()).isEqualTo(visitor.id().toString());
            assertThat(message.get("conversationId").asText()).isEqualTo(conversationId.toString());
            assertThat(message.get("createdAt").isTextual()).as("ISO timestamp, like the REST API").isTrue();
        }
        var history = chatService.listMessages(
                conversationId, new dev.chatforge.security.AuthenticatedUser(agent.id(), Role.AGENT), 0, 10);
        assertThat(history.totalElements()).isEqualTo(1);
    }

    @Test
    void messageSentOverRestIsAlsoBroadcast() throws Exception {
        Party visitor = newVisitor();
        Party agent = newAgent();
        UUID conversationId = openConversation(visitor);
        var agentInbox = subscribe(connect(agent).session(), topic(conversationId));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(visitor.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = rest.exchange("/api/conversations/" + conversationId + "/messages", HttpMethod.POST,
                new HttpEntity<>(Map.of("content", "hello over rest"), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(next(agentInbox).get("content").asText()).isEqualTo("hello over rest");
    }

    // --- authorization ---

    @Test
    void visitorCannotSubscribeToAnotherVisitorsConversation() throws Exception {
        UUID conversationId = openConversation(newVisitor());
        Connection intruder = connect(newVisitor());

        intruder.session().subscribe(topic(conversationId), new StompSessionHandlerAdapter() { });

        assertThat(intruder.events().poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("error");
    }

    @Test
    void subscribingToArbitraryDestinationsIsDenied() throws Exception {
        Connection connection = connect(newAgent());

        connection.session().subscribe("/topic/anything-else", new StompSessionHandlerAdapter() { });

        assertThat(connection.events().poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("error");
    }

    @Test
    void visitorCannotSendIntoAnotherVisitorsConversation() throws Exception {
        Party owner = newVisitor();
        UUID conversationId = openConversation(owner);
        var ownerInbox = subscribe(connect(owner).session(), topic(conversationId));
        Connection intruder = connect(newVisitor());
        var intruderErrors = subscribe(intruder.session(), "/user/queue/errors");

        sendOverStomp(intruder.session(), conversationId, "let me in");

        assertThat(next(intruderErrors).get("error").asText()).isEqualTo("not_found");
        assertNothingReceived(ownerInbox);
    }

    // --- error handling ---

    @Test
    void invalidMessageReturnsAnErrorToTheSenderOnly() throws Exception {
        Party visitor = newVisitor();
        UUID conversationId = openConversation(visitor);
        Connection connection = connect(visitor);
        var errors = subscribe(connection.session(), "/user/queue/errors");
        var inbox = subscribe(connection.session(), topic(conversationId));

        sendOverStomp(connection.session(), conversationId, "   ");

        assertThat(next(errors).get("error").asText()).isEqualTo("invalid_message");
        assertNothingReceived(inbox);
    }

    @Test
    void sendingToAClosedConversationReturnsAnError() throws Exception {
        Party visitor = newVisitor();
        UUID conversationId = openConversation(visitor);
        chatService.close(conversationId);
        Connection connection = connect(visitor);
        var errors = subscribe(connection.session(), "/user/queue/errors");

        sendOverStomp(connection.session(), conversationId, "anyone there?");

        assertThat(next(errors).get("error").asText()).isEqualTo("conversation_closed");
    }
}
