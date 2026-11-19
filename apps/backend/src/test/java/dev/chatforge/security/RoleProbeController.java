package dev.chatforge.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint under the agent-only path prefix, used to verify role-based access. */
@RestController
class RoleProbeController {

    @GetMapping("/api/agent/ping")
    String ping() {
        return "pong";
    }
}
