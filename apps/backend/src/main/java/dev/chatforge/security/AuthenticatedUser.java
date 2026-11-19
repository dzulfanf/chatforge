package dev.chatforge.security;

import java.util.UUID;

/** The authenticated principal extracted from a valid JWT. */
public record AuthenticatedUser(UUID id, Role role) {
}
