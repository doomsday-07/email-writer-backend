# Email Reply Generator — Backend

Spring Boot 4.0.1 (Java 25) backend for the AI-powered email reply app.
Provides:

- `POST /api/email/generate` — generate a context-aware reply (Gemini)
- `POST /api/email/translate` — translate an email to one of 10 languages
- `POST /api/email/send` — send a reply **from the user's own Gmail** via the
  Gmail API (per-user OAuth)
- `POST /api/oauth/exchange` — exchange a Google auth code for refresh +
  access tokens (stored in Postgres)
- `GET /api/oauth/status` — check whether the signed-in user has connected
  their Gmail

All `/api/**` endpoints require a valid Google ID token in
`Authorization: Bearer <idToken>`. ID tokens are verified against Google's
JWKS via Spring Security's OAuth2 Resource Server.

## Quick start (local dev)

```sh
cd email-writer-sb
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

The app uses an in-memory H2 database by default. To use Postgres locally:

```sh
export DATABASE_URL=jdbc:postgresql://localhost:5432/email_writer
export DATABASE_DRIVER=org.postgresql.Driver
export DATABASE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
```

### Required environment variables

| Variable | Required | Description |
|---|---|---|
| `GEMINI_API_URL` | yes | Google Gemini API base URL (`https://generativelanguage.googleapis.com`) |
| `GEMINI_API_KEY` | yes | Your Gemini API key |
| `GOOGLE_OAUTH_CLIENT_ID` | yes | OAuth Web Client ID (same one used in the frontend) |
| `GOOGLE_OAUTH_CLIENT_SECRET` | yes | OAuth Web Client secret |
| `GOOGLE_OAUTH_AUDIENCE` | no | Defaults to `GOOGLE_OAUTH_CLIENT_ID` |
| `CORS_ALLOWED_ORIGINS` | no | Comma-separated origins. Defaults to `http://localhost:5173,http://localhost:4173` |
| `DATABASE_URL` | no | Defaults to in-memory H2 |
| `RATE_LIMIT_CAPACITY` | no | Per-user burst size. Default `60` |
| `RATE_LIMIT_REFILL_PER_MINUTE` | no | Default `60` |
| `SERVER_PORT` | no | Default `9090` |

## Architecture

```
src/main/java/com/email/writer/
├── EmailWriterSbApplication.java   # Spring Boot entry point
├── EmailRequest.java               # @Valid request DTO
├── EmailGeneratorController.java   # /api/email/* endpoints
├── EmailGeneratorService.java      # Gemini API + Gmail dispatch
├── auth/
│   ├── OAuthController.java        # /api/oauth/{exchange,status}
│   ├── GoogleTokenService.java     # code↔token exchange, refresh
│   ├── GmailApiService.java        # send via Gmail API per-user
│   ├── GmailToken.java             # JPA entity
│   └── GmailTokenRepository.java   # Spring Data JPA
├── security/
│   └── SecurityConfig.java         # CORS, JWT auth, route protection
├── error/
│   ├── GlobalExceptionHandler.java # @ControllerAdvice
│   ├── ErrorResponse.java          # error envelope
│   ├── RateLimitExceededException.java
│   └── GmailNotConnectedException.java
└── ratelimit/
    ├── RateLimiter.java            # in-memory token bucket
    └── RateLimitFilter.java        # OncePerRequestFilter, per-user/IP
```

## Authentication flow

1. **Sign in with Google on the frontend** → Google returns an ID token (JWT).
2. Frontend sends `Authorization: Bearer <idToken>` to every backend call.
3. Backend's `oauth2ResourceServer().jwt()` decodes and verifies the JWT
   against Google's JWKS (`https://www.googleapis.com/oauth2/v3/certs`).
4. The verified `sub` (Google user ID) is the principal; `email` and `name`
   claims are extracted for sending.
5. When the user clicks **Connect Gmail**, the frontend uses Google's
   `auth-code` flow with scope `https://www.googleapis.com/auth/gmail.send`,
   `access_type=offline`, `prompt=consent` to obtain an auth code.
6. Frontend POSTs the code to `/api/oauth/exchange`. Backend exchanges the
   code (using its client secret) for access + refresh tokens and persists
   them keyed by `sub`.
7. On `/api/email/send`, the backend refreshes the access token if needed and
   calls `https://gmail.googleapis.com/upload/gmail/v1/users/me/messages/send`.

## Database

- Development: H2 in-memory, schema managed by Flyway.
- Production: Postgres (Render's `pserv`), schema managed by Flyway.
- The single table `gmail_token` stores one row per user.

To run Flyway migrations on an existing database, just start the app —
migrations run automatically.

## Security notes

- **Never** put `GOOGLE_OAUTH_CLIENT_SECRET`, `GEMINI_API_KEY`, or
  `DATABASE_PASSWORD` in `application.properties`. The `application.properties`
  in this repo reads them from environment variables only.
- For production, set these as **secret** environment variables in Render's
  dashboard (or use a secret manager).
- ID tokens are verified on every request. The backend does not issue
  session cookies or JWTs of its own — the Google ID token is the only
  credential.
- CORS is restricted to a known allow-list. The default
  `@CrossOrigin(origins = "*")` from earlier versions has been removed.

## Rate limiting

In-memory token bucket per user (or per IP if unauthenticated). Defaults:
60 requests per user per minute, burst capacity 60. Not distributed — for
multi-instance deploys swap the in-memory `RateLimiter` for a Redis-backed
implementation.

## What's still TODO (Wave 2+)

- Comprehensive tests (unit + integration) — see `EmailWriterSbApplicationTests`
  for the placeholder.
- Containerization (Dockerfile, multi-stage build).
- CI/CD (GitHub Actions: build + lint + test on every PR).
- Structured logging with request IDs (MDC).
- Prometheus metrics.
- API versioning (`/api/v1/email/...`).
- OpenAPI / springdoc.
- Email history persistence.
- Multiple Gmail identities per user (currently 1-to-1 with Google sub).
- React Router + multi-page frontend.
- TypeScript on the frontend.
