package dev.chatforge.chat;

import dev.chatforge.domain.ConversationStatus;
import dev.chatforge.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Agent-only endpoints (the /api/agent prefix is restricted to the AGENT role in SecurityConfig). */
@RestController
@RequestMapping("/api/agent/conversations")
class AgentConversationController {

    private final ChatService chat;

    AgentConversationController(ChatService chat) {
        this.chat = chat;
    }

    @GetMapping
    PageResponse<ConversationResponse> list(
            @RequestParam(defaultValue = "OPEN") ConversationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ChatService.DEFAULT_PAGE_SIZE) int size) {
        return chat.listConversations(status, page, size);
    }

    @PostMapping("/{id}/assign")
    ConversationResponse assign(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser agent) {
        return chat.assign(id, agent.id());
    }

    @PostMapping("/{id}/close")
    ConversationResponse close(@PathVariable UUID id) {
        return chat.close(id);
    }
}
