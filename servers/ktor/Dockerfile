# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage — compiles the Wasm dashboard, installs it into the Ktor server's
# resources, then builds the Ktor fat jar. Runs from the repository root so the
# Gradle version catalog and both modules are available.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# The Kotlin/Wasm dashboard build downloads Node.js, which needs libatomic.
RUN apt-get update && \
    apt-get install -y --no-install-recommends libatomic1 && \
    rm -rf /var/lib/apt/lists/*

# Copy the whole project (see .dockerignore for exclusions).
COPY . .

# installDashboard builds the production wasmJs bundle and syncs it into
# servers/ktor/src/main/resources/dashboard/ so buildFatJar bundles it in.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -x test \
      :dashboard:installDashboard \
      :servers:ktor:buildFatJar

# ---------------------------------------------------------------------------
# Runtime stage — small JRE image running the fat jar. Schema migrations are
# applied by the separate `migrate` service in docker-compose.yml.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget && \
    addgroup -S quietmetrix && adduser -S -G quietmetrix quietmetrix
COPY --from=builder /app/servers/ktor/build/libs/ktor-all.jar app.jar
USER quietmetrix
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/v1/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
