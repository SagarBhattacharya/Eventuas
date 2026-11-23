# Eventuas

Eventuas is a small event-management and ticketing API built with Java and Spring Boot. It supports event creation and publishing, ticket-tier management, ticket purchasing, organizer statistics, staff delegation, and QR-token-based gate check-in. A lightweight browser console is included for exercising the API without a separate frontend build.


## Features

- **Event lifecycle:** organizers create draft events, configure ticket tiers, publish events, and close them.
- **Public discovery:** browse published events with text, status, and date filters; view event details and active tiers without signing in.
- **Ticketing:** buy tickets, view a paginated ticket wallet, and retrieve QR check-in tokens.
- **Idempotent purchases:** `Idempotency-Key` makes retries of the same purchase return the previously created ticket instead of issuing another one.
- **Staff delegation:** event organizers can assign staff by email, list assigned staff, and revoke assignments.
- **Check-in:** assigned event staff can redeem ticket QR tokens; repeated scans are rejected.
- **Organizer reporting:** event statistics include capacity, sold/remaining tickets, check-ins, and per-tier metrics.
- **API documentation:** OpenAPI documentation is available in development.
- **Browser test console:** a static, dependency-free console provides organizer, buyer, and scanner actors, request history, and repeatable scenarios.

## Technology stack

- Java 25
- Spring Boot 4.1.2
- Spring MVC / Web
- Spring Security OAuth2 Resource Server (JWT)
- Spring Data JDBC and `JdbcClient`
- PostgreSQL 18
- Flyway migrations
- springdoc OpenAPI
- Docker Compose
- Casdoor for local identity and token issuance

## Requirements

- Java 25 (for running locally)
- Gradle 9.x (or use an installed Gradle version compatible with the project)
- Docker and Docker Compose
- A PostgreSQL instance if running the API outside Docker
- A QR signing secret supplied as `APP_QR_SECRET`

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Test Client Guide](docs/TEST_CLIENT.md)

## Local development

### 1. Start PostgreSQL and Casdoor

The Compose file starts PostgreSQL and Casdoor. PostgreSQL is exposed on `localhost:5432`; Casdoor is exposed on `localhost:9090`.

```bash
docker compose up -d postgres casdoor
```

### 2. Configure the API

The application reads its configuration from `src/main/resources/application.yaml` and environment variables. A local `development.env` file can be imported by Spring if present.

| Variable                   | Default                                       | Purpose                                                   |
|----------------------------|-----------------------------------------------|-----------------------------------------------------------|
| `DB_URL`                   | `jdbc:postgresql://localhost:5432/eventuas`   | JDBC connection URL                                       |
| `DB_USER`                  | `eventuas`                                    | Database user                                             |
| `DB_PASSWORD`              | `eventuas`                                    | Database password                                         |
| `ISSUER_URI`               | `http://localhost:9090`                       | JWT issuer expected by the resource server                |
| `APP_QR_SECRET`            | no application default                        | Secret used to sign QR tokens; must be configured         |
| `APP_CHECK_IN_GRACE`       | `PT6H`                                        | Check-in grace period after event end (ISO-8601 duration) |
| `APP_CHECK_IN_RATE_LIMIT`  | `60`                                          | Check-in attempts per scanner per minute                  |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Allowed browser origins                                   |

Set a sufficiently long, random `APP_QR_SECRET` before starting the API. Do not use the Compose development secret in a real deployment.

### 3. Run the API

```bash
./gradlew bootRun
```

The API listens on `http://localhost:8080`. Flyway applies database migrations at startup.

### 4. Open API documentation

In the development profile, visit:


- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

The production profile disables these endpoints.

## Docker Compose environment

The Compose file includes PostgreSQL, Casdoor, the Eventuas API, and the static browser client. To start the full stack:


```bash
export APP_QR_SECRET="replace-with-a-long-random-secret"
docker compose up --build
```

The services are exposed at:


| Service         | URL                     |
|-----------------|-------------------------|
| Browser console | `http://localhost:3000` |
| API             | `http://localhost:8080` |
| Casdoor         | `http://localhost:9090` |
| PostgreSQL      | `localhost:5432`        |

Compose configures the API with the `prod` profile, so Swagger/OpenAPI is disabled in that container. The browser client is served by Nginx and mounted read-only from `client`.

> **Local credentials:** The Compose setup and seeded Casdoor data are intended for local testing only. Change/remove seeded credentials, client secrets, database credentials, and QR signing secrets before deploying anywhere public.

## API overview

All protected API routes require a bearer JWT issued by the configured identity provider.

| Area                 | Endpoint                                       | Access                    |
|----------------------|------------------------------------------------|---------------------------|
| Current user         | `GET /users/me`                                | Authenticated             |
| Create event         | `POST /events`                                 | Organizer                 |
| Organizer event list | `GET /events`                                  | Organizer                 |
| Public event feed    | `GET /events/published`                        | Public                    |
| Public event detail  | `GET /events/published/{eventId}`              | Public                    |
| Publish event        | `POST /events/{eventId}/publish`               | Owner                     |
| Close event          | `POST /events/{eventId}/close`                 | Owner                     |
| Event statistics     | `GET /events/{eventId}/stats`                  | Owner                     |
| Create ticket tier   | `POST /events/{eventId}/tiers`                 | Owner                     |
| List owned tiers     | `GET /events/{eventId}/tiers`                  | Owner                     |
| List public tiers    | `GET /events/published/{eventId}/tiers`        | Public                    |
| Close tier           | `POST /events/{eventId}/tiers/{tierId}/close`  | Owner                     |
| Purchase ticket      | `POST /tiers/{tierId}/buy`                     | Authenticated buyer       |
| Ticket wallet        | `GET /tickets/me`                              | Authenticated buyer       |
| Ticket QR token      | `GET /tickets/{ticketId}/qr`                   | Ticket owner              |
| Redeem QR/check-in   | `POST /check-ins`                              | Authenticated event staff |
| Assign staff         | `POST /events/{eventId}/staff`                 | Event owner               |
| List staff           | `GET /events/{eventId}/staff`                  | Event owner               |
| Revoke staff         | `DELETE /events/{eventId}/staff/{staffUserId}` | Event owner               |

See [`docs/TEST_CLIENT.md`](docs/TEST_CLIENT.md) for request examples, workflows, and browser-console usage. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for implementation and data design.

## Tests

Run the automated test suite with:

```bash
./gradlew test
```

Tests cover event, tier, ticket, check-in, staff, user provisioning, pagination, QR signing/rate limiting, security hardening, and OpenAPI behavior. Integration tests use Testcontainers/PostgreSQL where configured.

## Repository layout

```text
.
├── client/                 # Static API test console
├── deploy/                 # Local database/auth bootstrap configuration
├── src/main/java/eventuas/  # API, domain, persistence, security and error handling
├── src/main/resources/
│   ├── application*.yaml
│   └── db/migration/        # Flyway SQL migrations
├── src/test/java/           # Unit, MVC/security, and integration tests
├── docs/
│   ├── ARCHITECTURE.md
│   └── TEST_CLIENT.md
├── Dockerfile
└── docker-compose.yaml
```

## License

This project is licensed under the [MIT License](LICENSE).

Copyright (c) 2026 Sagar Bhattacharya