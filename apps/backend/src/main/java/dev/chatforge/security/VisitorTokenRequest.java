package dev.chatforge.security;

import jakarta.validation.constraints.Size;

public record VisitorTokenRequest(@Size(max = 100) String displayName) {
}
