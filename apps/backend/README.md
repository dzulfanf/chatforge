# Chatforge Backend

> Status: **skeleton only, not yet built or tested.**

Spring Boot service for Chatforge. Its job:

- REST API (conversations, messages, widget configuration)
- Authentication and authorization
- Persistence
- Real-time messaging over STOMP on WebSocket

This is a JVM project built with Gradle (Kotlin DSL). It is not part of the pnpm workspace.

## Current state

The Spring Boot skeleton (Java 21, web, validation, actuator, JPA, Flyway, PostgreSQL) was written **without a JDK available, so it has never been compiled or run**. Known gaps:

- The Gradle wrapper (`gradlew`, `gradle/wrapper/`) is not generated yet. Run `gradle wrapper` with a local Gradle once, or generate it in the first verified build.
- The Spring Boot version (3.5.0) and the dependency set have not been checked against a real build.
- `ChatforgeApplicationTests` (context load against Testcontainers PostgreSQL) has not been run. It needs Docker.
- Security, WebSocket/STOMP and JWT dependencies are added in the steps that use them.

## Configuration

See [`.env.example`](./.env.example). Spring Boot does not read `.env` files by itself, so export the variables in your shell or set them in your IDE run configuration. `DB_PASSWORD` is required and has no default.

## Database

Start PostgreSQL from the repository root:

```bash
cp .env.example .env
docker compose up -d postgres
```
