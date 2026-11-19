package dev.chatforge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("chatforge-unit-secret-0123456789abcdef".getBytes());
    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString("chatforge-other-secret-0123456789abcdef".getBytes());

    private static JwtService service(String secret) {
        return new JwtService(new JwtProperties(secret, 3600, 86400));
    }

    @Test
    void roundTripsSubjectAndRole() {
        JwtService jwt = service(SECRET);
        UUID id = UUID.randomUUID();

        var user = jwt.parse(jwt.issue(id, Role.AGENT, Duration.ofMinutes(5)));

        assertThat(user).contains(new AuthenticatedUser(id, Role.AGENT));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwt = service(SECRET);

        String expired = jwt.issue(UUID.randomUUID(), Role.AGENT, Duration.ofSeconds(-60));

        assertThat(jwt.parse(expired)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String foreign = service(OTHER_SECRET).issue(UUID.randomUUID(), Role.AGENT, Duration.ofMinutes(5));

        assertThat(service(SECRET).parse(foreign)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(service(SECRET).parse("not-a-jwt")).isEmpty();
    }

    @Test
    void refusesSecretShorterThan256Bits() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());

        assertThatThrownBy(() -> service(shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void refusesSecretThatIsNotBase64() {
        assertThatThrownBy(() -> service("not base64 !!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }
}
