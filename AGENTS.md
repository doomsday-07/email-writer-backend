# Agents

## Build & Run

```bash
# Build (requires Java 25)
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  ./mvnw -f email-writer-sb/pom.xml clean package -DskipTests

# Run locally (auto-loads ../.env via springboot4-dotenv)
./mvnw -f email-writer-sb/pom.xml spring-boot:run

# Run packaged jar
java -jar email-writer-sb/target/email-writer-sb-0.0.1-SNAPSHOT.jar

# Run tests
./mvnw -f email-writer-sb/pom.xml test

# Docker
docker build -t email-writer-sb email-writer-sb/
docker run -p 10000:10000 -e PORT=10000 --env-file .env email-writer-sb
```

## Testing the API

```bash
# Health
curl http://localhost:9090/actuator/health

# Auth gate (expect 401)
curl -s -o /dev/null -w '%{http_code}' http://localhost:9090/api/oauth/status

# CORS preflight (expect 200 with Allow-Origin)
curl -s -D - -o /dev/null -X OPTIONS \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: POST' \
  http://localhost:9090/api/email/generate
```

## Useful paths

| Path | Purpose |
|---|---|
| `.env` | Repo-root env vars (gitignored) |
| `email-writer-sb/src/main/resources/application.properties` | Spring Boot config |
| `email-writer-sb/Dockerfile` | Multi-stage Docker build |
| `email-writer-sb/src/main/resources/db/migration/` | Flyway migrations |
