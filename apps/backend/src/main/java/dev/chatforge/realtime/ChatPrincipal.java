package dev.chatforge.realtime;

import dev.chatforge.security.AuthenticatedUser;
import java.security.Principal;

/** The authenticated user of a STOMP session. {@code getName()} is the user id, used to route user queues. */
public record ChatPrincipal(AuthenticatedUser user) implements Principal {

    @Override
    public String getName() {
        return user.id().toString();
    }
}
