package dev.chatforge.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Allowed browser origins. An empty list means no cross-origin access. */
@ConfigurationProperties("chatforge.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
