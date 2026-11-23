# Eventuas test console

Zero-build browser client for poking at the Eventuas API: three signed-in actors (organizer, buyer, scanner), a live request log, and one-click scenarios (happy path, oversell race, authorization).

## Run it

1. Start the stack and the API (from the repo root):
   ```bash
   docker compose up -d          # postgres, casdoor (seeded users), and this console
   ./gradlew bootRun             # API on :8080 (needs APP_QR_SECRET, see README / development.env)
   ```
2. Open http://localhost:3000 and click **Sign in all three** on the Scenarios tab.

No docker for the console? Any static server on port 3000 works (the OIDC redirect URI is `http://localhost:3000/`):
```bash
python3 -m http.server 3000 -d client
```

## Notes
- Sign-in uses Casdoor's password grant with the seeded users (`aarav`, `meera`, `rohan`, password `123`). The **via Casdoor page** button runs the real authorization-code + PKCE flow instead.
- Tokens live in `sessionStorage` only; **Reset** clears them. Config (URLs, client id) is at the top of `api.js`.
- The seeded client secret is a local dev value from `../deploy/init_data.json`. Do not reuse this pattern outside local testing.
