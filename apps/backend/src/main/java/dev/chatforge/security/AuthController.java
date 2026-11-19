package dev.chatforge.security;

import dev.chatforge.domain.Agent;
import dev.chatforge.domain.AgentRepository;
import dev.chatforge.domain.Visitor;
import dev.chatforge.domain.VisitorRepository;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AgentRepository agents;
    private final VisitorRepository visitors;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    /** Compared against when the email is unknown, so response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthController(
            AgentRepository agents,
            VisitorRepository visitors,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties) {
        this.agents = agents;
        this.visitors = visitors;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.dummyHash = passwordEncoder.encode("chatforge-dummy-password");
    }

    @PostMapping("/login")
    ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request) {
        Optional<Agent> agent = agents.findByEmail(request.email().trim());
        String hash = agent.map(Agent::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);

        if (agent.isEmpty() || !matches) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_credentials"));
        }
        long ttl = jwtProperties.expirationSeconds();
        String token = jwtService.issue(agent.get().getId(), Role.AGENT, Duration.ofSeconds(ttl));
        return ResponseEntity.ok(TokenResponse.bearer(token, ttl));
    }

    @PostMapping("/visitor")
    TokenResponse visitor(@Valid @RequestBody(required = false) VisitorTokenRequest request) {
        String displayName = request == null ? null : request.displayName();
        Visitor visitor = visitors.save(new Visitor(displayName));
        long ttl = jwtProperties.visitorExpirationSeconds();
        String token = jwtService.issue(visitor.getId(), Role.VISITOR, Duration.ofSeconds(ttl));
        return TokenResponse.bearer(token, ttl);
    }

    @GetMapping("/me")
    Map<String, String> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("id", user.id().toString(), "role", user.role().name());
    }
}
