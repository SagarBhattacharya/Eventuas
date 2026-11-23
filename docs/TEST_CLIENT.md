# Test Client and API Usage

This guide describes how to exercise Eventuas through the HTTP API and the included static browser console.

## 1. Start the stack

From the repository root:


```bash
export APP_QR_SECRET="replace-with-a-long-random-secret"
docker compose up --build
```

The browser console is at `http://localhost:3000`, the API at `http://localhost:8080`, and Casdoor at `http://localhost:9090`.

The Compose setup uses local seeded identity-provider users:

| Actor | Username | Password | Purpose |
|---|---|---|---|
| Organizer | `aarav` | `123` | Create/manage events, tiers, and staff; view stats |
| Buyer | `meera` | `123` | Browse and purchase tickets; view wallet and QR tokens |
| Scanner | `rohan` | `123` | Check in tickets after being assigned as event staff |

These credentials and the seeded client secret are strictly for local development.

## 2. Browser console

The client is static HTML, CSS, and JavaScript. No Node.js installation, package manager, or frontend build is needed.

### Use the bundled Nginx service

When the Compose stack is running, open:


```text
http://localhost:3000
```

### Serve the client separately

If the API and Casdoor are already running, serve `../client` from port 3000:


```bash
python3 -m http.server 3000 -d client
```

The redirect URI used by the client is the origin root (`http://localhost:3000/`). If you serve it from a different origin, update the Casdoor client redirect configuration and the client configuration in `../client/api.js` accordingly.

### Sign in

The console has organizer, buyer, and scanner actor panels. Sign in each actor using the quick password flow or the **via Casdoor page** authorization-code + PKCE flow. For scenario execution, use **Sign in all three** on the Scenarios tab.

The client stores access tokens in `sessionStorage`; signing out removes that actor's session. **Reset** clears the client state/session tokens. The request log displays method, path, actor/label, status, elapsed time, request ID, request/response data, and network failures; it retains up to 60 recent entries.

## 3. Built-in scenarios

Scenarios create their own event data and are intended to be run against a local test database. Repeated runs create additional events.

### Happy path

Exercises the full workflow:
1. Organizer creates a draft event.
2. Organizer creates a ticket tier.
3. Organizer assigns the scanner as event staff.
4. Organizer publishes the event.
5. Public client retrieves event details.
6. Buyer purchases a ticket.
7. Buyer retries the purchase using the same idempotency key; the same ticket should be returned.
8. Organizer checks that stock was decremented only once.
9. Buyer retrieves a QR token.
10. Scanner checks in the ticket.
11. A second scan is rejected.
12. Organizer closes the event.
13. A post-close purchase is rejected.

### Oversell race

Creates an event with one available ticket and sends six purchase requests concurrently using unique idempotency keys. Exactly one request should succeed; the remaining requests should be rejected because stock is exhausted. This validates the stock decrement's concurrency behavior.


### Authorization

Exercises visibility and access boundaries: a draft should not be publicly retrievable, and a buyer should not be able to perform organizer-only actions or check in as staff when not assigned.

## 4. API request examples

Examples assume the API runs at `http://localhost:8080`. Replace `{TOKEN}` and IDs with values obtained from actual responses. Public event endpoints do not require a bearer token; protected endpoints do.


### Authenticated request

```bash
curl -H "Authorization: Bearer {TOKEN}" \\
  http://localhost:8080/users/me
```

### Create a draft event

```bash
curl -X POST http://localhost:8080/events \\
  -H "Authorization: Bearer {ORGANIZER_TOKEN}" \\
  -H "Content-Type: application/json" \\
  -d '{
    "name": "Sample Conference",
    "description": "A demonstration event",
    "venue": "Kolkata",
    "startDate": "2027-01-10T10:00:00Z",
    "endDate": "2027-01-10T18:00:00Z",
    "salesStartDate": "2026-12-01T00:00:00Z",
    "salesEndDate": "2027-01-09T23:59:00Z"
  }'
```

The event starts in `DRAFT`. `startDate` and `endDate` are required; sales window values may be omitted.

### Create a ticket tier

```bash
curl -X POST http://localhost:8080/events/{EVENT_ID}/tiers \\
  -H "Authorization: Bearer {ORGANIZER_TOKEN}" \\
  -H "Content-Type: application/json" \\
  -d '{
    "tier": "General Admission",
    "description": "Standard entry",
    "availableQty": 100,
    "price": 49900,
    "currency": "INR"
  }'
```

Prices are integer units as supplied by the API; the API does not apply a decimal conversion. Currency is a three-letter currency code and is validated.

### Publish the event

```bash
curl -X POST http://localhost:8080/events/{EVENT_ID}/publish \\
  -H "Authorization: Bearer {ORGANIZER_TOKEN}"
```

An event must have at least one active tier before it can be published.

### Browse the public feed

```bash
curl "http://localhost:8080/events/published?q=conference&status=ACTIVE&page=0&size=20"
```

Filter parameters are optional:
- `q`: case-insensitive substring search across event name, description, and venue
- `status`: public status (`ACTIVE` or `CLOSED`; drafts are never public)
- `from`: ISO-8601 timestamp; events ending on/after this value are included
- `to`: ISO-8601 timestamp; events starting on/before this value are included
- `page`: zero-based page number (default `0`)
- `size`: page size (default `20`, maximum `100`)

### Get public event details and tiers

```bash
curl http://localhost:8080/events/published/{EVENT_ID}
curl http://localhost:8080/events/published/{EVENT_ID}/tiers
```

### Buy a ticket

```bash
curl -X POST http://localhost:8080/tiers/{TIER_ID}/buy \\
  -H "Authorization: Bearer {BUYER_TOKEN}" \\
  -H "Idempotency-Key: purchase-unique-key-001"
```

The endpoint returns the ticket on success. For retries of the same logical purchase, reuse the same key for the same buyer. Use a new key for a separate purchase. Keys must be nonblank and no longer than 128 characters.

### List the buyer's tickets

```bash
curl "http://localhost:8080/tickets/me?page=0&size=20&checkedIn=false&upcoming=true" \\
  -H "Authorization: Bearer {BUYER_TOKEN}"
```

Optional wallet filters:
- `q`: case-insensitive search across event name and venue
- `checkedIn`: filter redeemed/non-redeemed tickets
- `upcoming`: filter upcoming versus past events
- `page`, `size`: pagination

### Retrieve a ticket QR token

```bash
curl http://localhost:8080/tickets/{TICKET_ID}/qr \\
  -H "Authorization: Bearer {BUYER_TOKEN}"
```

The ticket owner must request the token. The response contains a `qrToken` string; send this token to the check-in endpoint.

### Assign a scanner as event staff

```bash
curl -X POST http://localhost:8080/events/{EVENT_ID}/staff \\
  -H "Authorization: Bearer {ORGANIZER_TOKEN}" \\
  -H "Content-Type: application/json" \\
  -d '{"email":"scanner@example.test"}'
```

The email must correspond to a user known to the application. In the browser scenario, the scanner's provisioned email is used automatically.

### Check in a ticket

```bash
curl -X POST http://localhost:8080/check-ins \\
  -H "Authorization: Bearer {SCANNER_TOKEN}" \\
  -H "Content-Type: application/json" \\
  -d '{"qrToken":"{QR_TOKEN}"}'
```

The scanner must be assigned to the event. A ticket may only be checked in once. Check-in is limited to an active event and through the configured grace period after the event ends (six hours by default). Scanner attempts are rate limited (60 per minute by default).

### Close an event

```bash
curl -X POST http://localhost:8080/events/{EVENT_ID}/close \\
  -H "Authorization: Bearer {ORGANIZER_TOKEN}"
```

Closing an event closes its active tiers as well and prevents further sales/check-ins.

### Event statistics

```bash
curl http://localhost:8080/events/{EVENT_ID}/stats \\
  -H "Authorization: Bearer {ORGANIZER_TOKEN}"
```

The response includes total capacity, sold and remaining tickets, check-ins, and per-tier counts/revenue.

### Staff management

```bash
# List staff
curl http://localhost:8080/events/{EVENT_ID}/staff \\
  -H "Authorization: Bearer {ORGANIZER_TOKEN}"

# Revoke a staff assignment
curl -X DELETE http://localhost:8080/events/{EVENT_ID}/staff/{STAFF_USER_ID} \\
  -H "Authorization: Bearer {ORGANIZER_TOKEN}"
```

## 5. Error responses

Errors use the `application/problem+json` media type and include a machine-readable `code` property. Common HTTP statuses:

| Status | Meaning | Typical examples |
|---|---|---|
| `400 Bad Request` | Invalid input or request | Validation failure, malformed JSON, invalid QR token, invalid idempotency key, invalid event/sales dates |
| `401 Unauthorized` | Missing, invalid, or expired bearer token | Protected route without valid JWT |
| `403 Forbidden` | Authenticated user lacks permission | Not event owner; not ticket owner; unauthorized staff action |
| `404 Not Found` | Resource not found or not publicly visible | Unknown event/tier/ticket/user; draft event accessed publicly |
| `409 Conflict` | Operation conflicts with current state | Sold out, closed event/tier, duplicate active tier, repeated check-in, invalid lifecycle transition |
| `429 Too Many Requests` | Scanner rate limit exceeded | Check-in attempts over configured limit |
| `500 Internal Server Error` | Unexpected server failure | Generic response; details are logged server-side |

Example problem response:
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "The ticket has already been checked in.",
  "code": "TICKET_ALREADY_CHECKED_IN"
}
```
The exact `title`, `detail`, and error code depend on the failure. Do not assume this sample code or detail for a particular response.

## 6. Request tracing

The API returns `X-Request-Id` for requests. The browser console captures this value in its request log. If you need to investigate a failed request, include the request ID with server log output.
