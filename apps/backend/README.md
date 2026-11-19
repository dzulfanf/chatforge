# Chatforge Backend

> Status: **not yet implemented.**

Spring Boot service for Chatforge. Its job:

- REST API (conversations, messages, widget configuration)
- Authentication and authorization
- Persistence
- Real-time messaging over STOMP on WebSocket

This is a JVM project. It is built with its own build tool (Gradle or Maven, not decided yet) and is not part of the pnpm workspace.

Configuration: see [`.env.example`](./.env.example).
