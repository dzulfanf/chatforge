package dev.chatforge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chatforge.TestcontainersConfiguration;
import dev.chatforge.domain.Agent;
import dev.chatforge.domain.AgentRepository;
import dev.chatforge.domain.VisitorRepository;
import java.time.Duration;
import java.util.Base64;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    private static final String PASSWORD = "s3cret-pass";
    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString("chatforge-other-secret-0123456789abcdef".getBytes());

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AgentRepository agents;
    @Autowired VisitorRepository visitors;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private Agent createAgent() {
        return agents.save(new Agent(
                "agent-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode(PASSWORD), "Agent"));
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    private String loginToken(Agent agent) throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", agent.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }

    private String visitorToken() throws Exception {
        String response = mvc.perform(post("/api/auth/visitor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }

    // --- agent login ---

    @Test
    void loginIssuesAgentTokenForValidCredentials() throws Exception {
        Agent agent = createAgent();

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", agent.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        assertThat(jwtService.parse(loginToken(agent)))
                .contains(new AuthenticatedUser(agent.getId(), Role.AGENT));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        Agent agent = createAgent();

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", agent.getEmail(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void loginRejectsUnknownEmailWithTheSameResponse() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "nobody@example.com", "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void loginRejectsBlankFields() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "", "password", ""))))
                .andExpect(status().isBadRequest());
    }

    // --- visitor token ---

    @Test
    void visitorEndpointCreatesVisitorAndIssuesVisitorToken() throws Exception {
        AuthenticatedUser user = jwtService.parse(visitorToken()).orElseThrow();

        assertThat(user.role()).isEqualTo(Role.VISITOR);
        assertThat(visitors.existsById(user.id())).isTrue();
    }

    // --- token validation ---

    @Test
    void protectedEndpointRejectsMissingToken() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void protectedEndpointRejectsGarbageToken() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsExpiredToken() throws Exception {
        String expired = jwtService.issue(UUID.randomUUID(), Role.AGENT, Duration.ofSeconds(-60));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsTamperedSignature() throws Exception {
        String token = loginToken(createAgent());
        String[] parts = token.split("\\.");
        // Change the first signature character: the last one can carry unused padding bits.
        char first = parts[2].charAt(0);
        String tampered = parts[0] + "." + parts[1] + "." + (first == 'A' ? 'B' : 'A') + parts[2].substring(1);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsTokenSignedWithAnotherKey() throws Exception {
        JwtService foreign = new JwtService(new JwtProperties(OTHER_SECRET, 3600, 86400));
        String token = foreign.issue(UUID.randomUUID(), Role.AGENT, Duration.ofMinutes(5));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointAcceptsValidToken() throws Exception {
        Agent agent = createAgent();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + loginToken(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agent.getId().toString()))
                .andExpect(jsonPath("$.role").value("AGENT"));
    }

    // --- role-based access ---

    @Test
    void agentOnlyPathRejectsAnonymousRequests() throws Exception {
        mvc.perform(get("/api/agent/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void agentOnlyPathRejectsVisitorToken() throws Exception {
        mvc.perform(get("/api/agent/ping").header("Authorization", "Bearer " + visitorToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void agentOnlyPathAcceptsAgentToken() throws Exception {
        mvc.perform(get("/api/agent/ping").header("Authorization", "Bearer " + loginToken(createAgent())))
                .andExpect(status().isOk());
    }

    // --- CORS and public endpoints ---

    @Test
    void corsAllowsConfiguredOriginOnly() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mvc.perform(options("/api/auth/login")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
