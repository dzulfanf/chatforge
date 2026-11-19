package dev.chatforge.chat;

/** Thrown when a resource does not exist or the caller is not allowed to know that it exists. */
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("not_found");
    }
}
