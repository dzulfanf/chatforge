package dev.chatforge.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chatforge.TestcontainersConfiguration;
import dev.chatforge.domain.Agent;
import dev.chatforge.domain.AgentRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChatIntegrationTest {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AgentRepository agents;
    @Autowired PasswordEncoder passwordEncoder;

    // --- helpers ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String visitorToken() throws Exception {
        return read(mvc.perform(post("/api/auth/visitor").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .get("accessToken").asText();
    }

    private record AgentSession(UUID id, String token) {
    }

    private AgentSession agentSession() throws Exception {
        Agent agent = agents.save(new Agent(
                "agent-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode(PASSWORD), "Agent"));
        String token = read(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", agent.getEmail(), "password", PASSWORD)))))
                .get("accessToken").asText();
        return new AgentSession(agent.getId(), token);
    }

    private UUID openConversation(String visitorToken) throws Exception {
        return UUID.fromString(read(mvc.perform(post("/api/conversations")
                        .header("Authorization", bearer(visitorToken))))
                .get("id").asText());
    }

    private ResultActions send(UUID conversationId, String token, String content) throws Exception {
        return mvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("content", content))));
    }

    // --- opening a conversation ---

    @Test
    void visitorOpensConversationAndRepeatCallReturnsTheSameOne() throws Exception {
        String visitor = visitorToken();

        var first = mvc.perform(post("/api/conversations").header("Authorization", bearer(visitor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));
        UUID id = UUID.fromString(read(first).get("id").asText());

        mvc.perform(post("/api/conversations").header("Authorization", bearer(visitor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void agentCannotOpenAConversation() throws Exception {
        mvc.perform(post("/api/conversations").header("Authorization", bearer(agentSession().token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void openingAConversationRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/conversations")).andExpect(status().isUnauthorized());
    }

    // --- messages ---

    @Test
    void visitorAndAgentExchangeMessagesInChronologicalOrder() throws Exception {
        String visitor = visitorToken();
        AgentSession agent = agentSession();
        UUID conversationId = openConversation(visitor);

        send(conversationId, visitor, "hello").andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderType").value("VISITOR"))
                .andExpect(jsonPath("$.conversationId").value(conversationId.toString()));
        send(conversationId, agent.token(), "hi, how can I help?").andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderType").value("AGENT"))
                .andExpect(jsonPath("$.senderId").value(agent.id().toString()));

        mvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(visitor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].content").value("hello"))
                .andExpect(jsonPath("$.content[1].content").value("hi, how can I help?"));
    }

    @Test
    void messageHistoryIsPaginated() throws Exception {
        String visitor = visitorToken();
        UUID conversationId = openConversation(visitor);
        for (String text : new String[] {"one", "two", "three"}) {
            send(conversationId, visitor, text).andExpect(status().isCreated());
        }

        mvc.perform(get("/api/conversations/" + conversationId + "/messages?page=1&size=2")
                        .header("Authorization", bearer(visitor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content").value("three"));
    }

    @Test
    void oversizedPageSizeIsClamped() throws Exception {
        String visitor = visitorToken();
        UUID conversationId = openConversation(visitor);

        mvc.perform(get("/api/conversations/" + conversationId + "/messages?size=100000")
                        .header("Authorization", bearer(visitor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void rejectsBlankAndOversizedMessages() throws Exception {
        String visitor = visitorToken();
        UUID conversationId = openConversation(visitor);

        send(conversationId, visitor, "   ").andExpect(status().isBadRequest());
        send(conversationId, visitor, "x".repeat(4001)).andExpect(status().isBadRequest());
        send(conversationId, visitor, "x".repeat(4000)).andExpect(status().isCreated());
    }

    // --- access control ---

    @Test
    void visitorCannotSeeOrWriteToAnotherVisitorsConversation() throws Exception {
        UUID conversationId = openConversation(visitorToken());
        String intruder = visitorToken();

        mvc.perform(get("/api/conversations/" + conversationId).header("Authorization", bearer(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isNotFound());
        send(conversationId, intruder, "let me in").andExpect(status().isNotFound());
    }

    @Test
    void agentCanReadAnyConversation() throws Exception {
        UUID conversationId = openConversation(visitorToken());

        mvc.perform(get("/api/conversations/" + conversationId)
                        .header("Authorization", bearer(agentSession().token())))
                .andExpect(status().isOk());
    }

    @Test
    void messagesRequireAuthentication() throws Exception {
        UUID conversationId = openConversation(visitorToken());

        mvc.perform(get("/api/conversations/" + conversationId + "/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownAndMalformedConversationIds() throws Exception {
        String visitor = visitorToken();

        mvc.perform(get("/api/conversations/" + UUID.randomUUID()).header("Authorization", bearer(visitor)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/conversations/not-a-uuid").header("Authorization", bearer(visitor)))
                .andExpect(status().isBadRequest());
    }

    // --- agent endpoints ---

    @Test
    void agentListsOpenConversationsAndVisitorCannot() throws Exception {
        UUID conversationId = openConversation(visitorToken());
        AgentSession agent = agentSession();

        mvc.perform(get("/api/agent/conversations?size=100").header("Authorization", bearer(agent.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + conversationId + "')]").exists());

        mvc.perform(get("/api/agent/conversations").header("Authorization", bearer(visitorToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentAssignsAndClosesAConversation() throws Exception {
        String visitor = visitorToken();
        AgentSession agent = agentSession();
        UUID conversationId = openConversation(visitor);

        mvc.perform(post("/api/agent/conversations/" + conversationId + "/assign")
                        .header("Authorization", bearer(agent.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedAgentId").value(agent.id().toString()));

        mvc.perform(post("/api/agent/conversations/" + conversationId + "/close")
                        .header("Authorization", bearer(agent.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // Closed conversations reject new messages and assignment.
        send(conversationId, visitor, "anyone there?").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conversation_closed"));
        mvc.perform(post("/api/agent/conversations/" + conversationId + "/assign")
                        .header("Authorization", bearer(agent.token())))
                .andExpect(status().isConflict());

        // History stays readable, and the visitor can start a fresh conversation.
        mvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(visitor)))
                .andExpect(status().isOk());
        assertThat(openConversation(visitor)).isNotEqualTo(conversationId);
    }

    @Test
    void visitorCannotAssignOrCloseConversations() throws Exception {
        String visitor = visitorToken();
        UUID conversationId = openConversation(visitor);

        mvc.perform(post("/api/agent/conversations/" + conversationId + "/close")
                        .header("Authorization", bearer(visitor)))
                .andExpect(status().isForbidden());
    }
}
