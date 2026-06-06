# syntax=docker/dockerfile:1.7
# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Cache dependencies in a separate layer
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests -Dmaven.test.skip=true -Dmaven.javadoc.skip=true \
        dependency:go-offline

# Build the fat jar (target/email-writer-sb-0.0.1-SNAPSHOT.jar)
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests -Dmaven.test.skip=true -Dmaven.javadoc.skip=true \
        -Dmaven.gitcommitid.skip=true package

# ---------- runtime stage ----------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Run as non-root
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/target/email-writer-sb-0.0.1-SNAPSHOT.jar app.jar

# Render sets PORT=10000 for web services; Spring Boot reads it natively.
EXPOSE 10000

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider \
        http://localhost:10000/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
