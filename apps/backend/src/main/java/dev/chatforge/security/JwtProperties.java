package dev.chatforge.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings. {@code secret} must be base64-encoded and decode to at least 32 bytes
 * (checked by {@link JwtService} at startup).
 */
@Validated
@ConfigurationProperties("chatforge.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @DefaultValue("3600") @Positive long expirationSeconds,
        @DefaultValue("86400") @Positive long visitorExpirationSeconds) {
}
