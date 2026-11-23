# Architecture

## 1. Overview

Eventuas is implemented as a modular Spring Boot application. It is a single deployable API backed by PostgreSQL, not a collection of independently deployed microservices. Feature packages separate the domain responsibilities while services coordinate business operations and repositories handle persistence.

The main runtime components are:

- **Eventuas API:** Spring MVC endpoints and application/domain services.
- **PostgreSQL:** persistent event, ticket, user, staff, check-in, and idempotency data.
- **Casdoor:** local identity provider used by the development stack.
- **Static browser console:** an Nginx-served JavaScript client used to exercise flows.

## 2. Request path

For protected API requests, the high-level flow is:

1. The client obtains a JWT from the configured identity provider.
2. Spring Security's OAuth2 resource-server support validates the bearer token.
3. `UserProvisionFilter` validates that the JWT subject is a UUID and synchronizes the user identity (ID, name, email) into the application database. It caches claims for five minutes, with a maximum cache size of 10,000 entries; the cache is cleared when its size threshold is reached.
4. `CurrentUserArgumentResolver` resolves the authenticated user ID for controller parameters annotated with `@CurrentUser`.
5. A controller delegates to a feature service.
6. The service applies domain rules and invokes repositories. Transactional methods use Spring transaction management.
7. A response is returned. `RequestIdFilter` propagates an `X-Request-Id` value (or generates a UUID) and places it in MDC for logging.

The request ID is returned as a response header and included in the configured console log pattern. A caller-supplied ID is accepted when nonblank and no longer than 64 characters.

## 3. Modules

| Package | Responsibility |
|---|---|
| `event` | Event lifecycle, ownership, public/organizer queries, event stats, and event access checks |
| `ticket_tier` | Tier creation, quantity/price/currency validation, active/closed state, and tier stock |
| `ticket` | Ticket purchase, ticket wallet queries, QR token issuance, and purchase idempotency |
| `check_in` | QR signature verification, check-in redemption, scanner rate limiting, and check-in persistence |
| `event_staff` | Organizer-managed staff assignment, lookup, and revocation |
| `user` | Application user records synchronized from identity tokens and current-user endpoint |
| `pagination` | Page normalization, paged response metadata, and parameterized SQL filter helper |
 | `config` | Security, CORS, OpenAPI, user provisioning, request IDs, and web configuration |
 | `errors` | Domain exceptions, error codes, and global HTTP error mapping |


The typical controller/service/repository boundary is:


```text
HTTP request
    │
    ▼
Controller ── DTO validation / route binding
    │
    ▼
Feature Service ── domain rules / transaction boundary
    │
    ├── Domain objects
    ├── Access checks
    └── Repository / JdbcClient
             │
             ▼
        PostgreSQL
```

## 4. Event lifecycle

Events have three states:

- `DRAFT`: private to the organizer; tiers can be managed; cannot be purchased or publicly retrieved.
- `ACTIVE`: published, publicly discoverable, and eligible for ticket sales subject to the sales window. Check-in is permitted while the event is active and within the configured post-event grace period.
- `CLOSED`: closed by the organizer; ticket sales and check-in are no longer allowed. Active tiers are also closed when the event is closed.

An event must have an end time after its start time. If both sales-window endpoints are provided, the sales end must be after sales start. Publishing requires at least one active tier. Publishing is a draft-to-active transition; closing an already closed event is rejected.

Event ownership is enforced through `EventAccess`. Public routes expose only non-draft events. Public event feed filters include text search, status, and date overlap constraints.

## 5. Ticket tiers and sales

A ticket tier belongs to an event and contains a name, description, available quantity, integer price, three-letter currency code, status, and timestamps. Tier names are unique per event among active tiers. Tier quantities must be positive, prices cannot be negative, and the configured request validation limits quantities and prices.

A tier can be closed by its event organizer. Public tier listing includes active tiers for a publicly visible event.

Ticket purchase is performed transactionally. The service:

1. Normalizes and validates the `Idempotency-Key` (nonblank, maximum 128 characters).
2. Claims the key for the buyer in the idempotency table. The uniqueness boundary is buyer + key.
3. If the key has already been claimed and bound, returns the ticket already associated with it.
4. Validates the tier and event purchase state/sales window.
5. Decrements stock with a repository-level conditional operation. A zero-row decrement indicates no stock was available and is treated as sold out.
6. Creates the ticket and binds the idempotency key to that ticket.

This protects retries and prevents the oversell race from relying on a read-then-write stock check alone. A repeated key replays the original purchase; clients should generate a new key for a distinct purchase.

## 6. Ticket wallet and QR check-in

The ticket wallet is a buyer-scoped paginated feed that includes event and tier details, price/currency, purchase time, and check-in state. Query filters support text search, checked-in status, and upcoming/past event selection.

A ticket owner can request a QR token for their ticket. Tokens are signed by `QrSigner` using the configured QR secret; the token is not a ticket ID by itself. The QR endpoint checks ownership before issuing a token.

At check-in:

1. A scanner submits a QR token.
2. The scanner rate limiter applies a per-user attempt limit (configurable; default 60 per minute).
3. The QR signer validates the token signature and extracts the ticket identity.
4. The service verifies ticket/event eligibility, event state, and the check-in time window.
5. Event staff authorization is checked for the event.
6. A check-in record is created. The database's unique ticket constraint ensures a ticket cannot be redeemed twice; repeated redemption returns a conflict.

The check-in window is event-active and ends after the event end plus the configured grace period (default six hours). The check-in response records the ticket, scanner, and redemption timestamp.

## 7. Staff delegation

Event organizers assign staff by email. Staff identity is resolved through the synchronized users table. Staff records are scoped to an event and uniquely identify an event/user pair. Organizers can list assigned staff and revoke an assignment. Staff authorization is used for check-in and does not grant organizer ownership of event-management actions.

## 8. Persistence

Spring Data JDBC and `JdbcClient` are used for persistence. Flyway runs versioned SQL migrations at application startup.

### Tables

| Table | Purpose |
|---|---|
| `users` | Identity profile synchronized from JWT claims |
| `events` | Event metadata, lifecycle state, owner, event and sales windows |
| `ticket_tiers` | Ticket tier details, stock, price, currency, status |
| `tickets` | Ticket ownership and tier association |
| `event_staffs` | Event-to-user staff assignments |
| `check_ins` | One-time ticket redemption records |
| `idempotency_keys` | Buyer-scoped purchase retry keys and ticket binding |

### Migrations

- `V1__init.sql`: base users, events, ticket tiers, tickets, event staff, and check-in schema, including key constraints and indexes.
- `V2__ticket_check_in.sql`: check-in-related schema migration (the resulting check-in table is part of the schema).
- `V3__events_pagination.sql`: trigram search indexes and feed indexes for event and ticket listing.
- `V4__ticket_idempotency.sql`: buyer-scoped idempotency keys and ticket binding.

PostgreSQL features used include UUID identifiers, timestamp-with-time-zone columns, partial unique indexes, trigram (`pg_trgm`) indexes, and constraints. Event search uses case-insensitive substring matching and is accelerated by trigram indexes.

## 9. Pagination and query filtering

`PageQuery` normalizes page values: page defaults to zero and negative pages become zero; size defaults to 20 and is capped at 100. `PagedResponse` returns:

- `items`
- `page`
- `size`
- `totalElements`
- `totalPages`
- `hasNext`

The event and ticket query repositories build optional predicates through the `SqlFilter` helper and bind values rather than interpolating filter input directly. Feed ordering is deterministic, with tie-break IDs included in sort order.

## 10. Authentication, authorization, and security boundaries

The API is a stateless OAuth2 JWT resource server. It does not create server-side sessions. CSRF is disabled for this stateless bearer-token API.

- Health endpoints are public.
- OpenAPI and Swagger routes are public when enabled.
- Public event GET routes are unauthenticated.
- Other endpoints require authentication.
- Ownership checks are performed by services/access helpers, not just by route authentication.
- Staff authorization is distinct from organizer ownership.
- CORS is configured through `APP_CORS_ALLOWED_ORIGINS`; methods and headers are explicitly constrained.
- User provisioning rejects missing/non-UUID JWT subjects and uses a fallback email sentinel when the JWT has no email claim.

The development Compose configuration seeds identity-provider users and client credentials. Those values are for local testing and must not be reused in production.

## 11. Error handling and observability

`GlobalExceptionHandler` maps domain and validation failures to HTTP responses using Spring `ProblemDetail` with an application `code` property. The application also handles malformed JSON, invalid parameter types, missing headers/parameters, database integrity conflicts, and unexpected exceptions. Unexpected errors are logged and returned with a generic internal-error detail.

`RequestIdFilter` accepts or generates a request ID, returns it in `X-Request-Id`, and adds it to the logging MDC. Structured key/value logging is used for significant business events and rejections.

Actuator exposes health and info endpoints; health probes are enabled. Docker health checks use the Actuator health endpoint.

## 12. Browser test console

The `../client` directory is a static JavaScript console with no frontend build or dependency installation. It provides three actors (organizer, buyer, scanner), API request logging, token handling, and scenario runners. It is a testing/demo client, not a production customer UI. See [`TEST_CLIENT.md`](TEST_CLIENT.md).

## 13. Design constraints and future considerations

- The application is currently a single deployable Spring Boot service; the package boundaries are internal modular boundaries.
- Casdoor is part of the local development environment. A production identity provider and secure client configuration should be selected and configured independently.
- The local Compose setup includes seeded credentials and a development QR secret fallback; these must be replaced before any public deployment.
- The browser client uses local-development token flows and stores tokens in session storage; this is not a production authentication UX.
- Event/ticket business logic and concurrency correctness are exercised by tests; production load, deployment, monitoring, backup, and disaster-recovery procedures are outside this repository's current documentation scope.
