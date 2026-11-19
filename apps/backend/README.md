# Chatforge Backend

> Status: **persistence layer only.** The schema, entities and repositories exist and are tested. There are no REST endpoints, authentication or messaging yet.

Spring Boot service for Chatforge. Its job:

- REST API (conversations, messages, widget configuration)
- Authentication and authorization
- Persistence
- Real-time messaging over STOMP on WebSocket

This is a JVM project built with Gradle (Kotlin DSL, wrapper included). It is not part of the pnpm workspace.

## Current state

Verified (Java 21, Gradle 8.14, run in the `gradle:8.14-jdk21` Docker image):

- `./gradlew build -x test` succeeds.
- `ChatforgeApplicationTests` passes: the Spring context loads against a Testcontainers PostgreSQL 17 instance with Flyway enabled.
- `PersistenceIntegrationTest` (5 tests) passes against PostgreSQL 17: agent lookup and unique email, message ordering per conversation, conversation queries by visitor and status, and the blank-content check constraint.

Not done yet:

- Security, WebSocket/STOMP and JWT dependencies, added in the steps that use them.
- REST endpoints, authentication and messaging.

## Data model

Flyway migration `V1__create_core_tables.sql` creates `agents`, `visitors`, `conversations` and `messages` (UUID keys, `timestamptz` timestamps, string enums with check constraints). `messages.sender_id` points to an agent or a visitor depending on `sender_type`, so it has no foreign key and must be validated in the application layer.

## Prerequisites

- JDK 21
- Docker (the tests start PostgreSQL with Testcontainers)

Testcontainers is pinned to 1.21.4 in `build.gradle.kts` because the version managed by Spring Boot 3.5.0 (1.21.1) cannot talk to Docker Engine 29+ (HTTP 400). Remove the override when Spring Boot's managed version supports it.

## Configuration

See [`.env.example`](./.env.example). Spring Boot does not read `.env` files by itself, so export the variables in your shell or set them in your IDE run configuration. `DB_PASSWORD` is required and has no default.

## Commands

```bash
./gradlew build         # compile and test
./gradlew test          # tests only (needs Docker)
./gradlew bootRun       # run the app (needs PostgreSQL and DB_PASSWORD)
```

## Database

Start PostgreSQL from the repository root:

```bash
cp .env.example .env
docker compose up -d postgres
```
