package dev.chatforge.chat;

import dev.chatforge.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints shared by visitors (their own conversation) and agents (any conversation). */
@RestController
@RequestMapping("/api/conversations")
class ConversationController {

    private final ChatService chat;

    ConversationController(ChatService chat) {
        this.chat = chat;
    }

    /** Visitor only (enforced in SecurityConfig). Returns 201 when created, 200 for the existing open one. */
    @PostMapping
    ResponseEntity<ConversationResponse> open(@AuthenticationPrincipal AuthenticatedUser user) {
        ChatService.OpenResult result = chat.openConversation(user.id());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.conversation());
    }

    @GetMapping("/{id}")
    ConversationResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return chat.getConversation(id, user);
    }

    @GetMapping("/{id}/messages")
    PageResponse<MessageResponse> messages(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ChatService.DEFAULT_PAGE_SIZE) int size) {
        return chat.listMessages(id, user, page, size);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse send(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SendMessageRequest request) {
        return chat.sendMessage(id, user, request.content());
    }
}
