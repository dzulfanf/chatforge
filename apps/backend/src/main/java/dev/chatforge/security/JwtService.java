package dev.chatforge.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        byte[] secret;
        try {
            secret = Decoders.BASE64.decode(properties.secret());
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET must be base64-encoded", e);
        }
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must decode to at least " + MIN_SECRET_BYTES + " bytes (256 bits)");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issue(UUID subject, Role role, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Returns the user for a valid, unexpired, correctly signed token; empty otherwise. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
