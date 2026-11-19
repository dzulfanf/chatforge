# Chatforge Backend

> Status: **persistence and authentication.** The schema and JWT authentication exist and are tested. Conversation/message REST endpoints and STOMP messaging are not implemented yet.

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
- 28 tests pass in total. `AuthIntegrationTest` (16 tests) and `JwtServiceTest` (6 tests) cover login, visitor tokens, expired/tampered/foreign tokens, role checks and CORS.
- `PersistenceIntegrationTest` (5 tests) passes against PostgreSQL 17: agent lookup and unique email, message ordering per conversation, conversation queries by visitor and status, and the blank-content check constraint.

Not done yet:

- Security, WebSocket/STOMP and JWT dependencies, added in the steps that use them.
- Conversation and message REST endpoints, and STOMP messaging.
- Provisioning of agent accounts: there is no registration endpoint, so agents must be inserted directly for now (hash passwords with BCrypt).
- Rate limiting on `/api/auth/*`. `POST /api/auth/visitor` is unauthenticated by design and creates a row per call, so it can be abused until limits are added.

## Authentication

Stateless JWT (HS256). Tokens carry the user id as `sub` and a `role` claim (`AGENT` or `VISITOR`).

| Endpoint | Access | Purpose |
| --- | --- | --- |
| `POST /api/auth/login` | public | Agent email and password, returns a bearer token (`JWT_EXPIRATION`, default 1 h) |
| `POST /api/auth/visitor` | public | Creates a visitor, returns a bearer token (`JWT_VISITOR_EXPIRATION`, default 24 h) |
| `GET /api/auth/me` | any valid token | Returns the caller's id and role |
| `/api/agent/**` | `AGENT` only | Reserved prefix for agent endpoints |

Notes:

- `JWT_SECRET` must be base64-encoded and decode to at least 32 bytes (`openssl rand -base64 32`). The app refuses to start otherwise.
- Invalid, expired, tampered or foreign-signed tokens get `401`; a valid token with the wrong role gets `403`.
- Login returns the same `401` for an unknown email and a wrong password, and compares against a dummy hash so timing does not reveal which emails exist.
- Cross-origin access is limited to `CORS_ALLOWED_ORIGINS` (comma-separated).
- Tokens are not revocable before they expire.

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
