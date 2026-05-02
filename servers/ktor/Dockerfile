FROM gradle:8.12-jdk21 AS builder
WORKDIR /app
COPY servers/ktor/build.gradle.kts servers/ktor/settings.gradle.kts* ./
COPY servers/ktor/migrations/ ./migrations/
COPY servers/ktor/src/ ./src/
RUN gradle build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/quietmetrix-ktor-*.jar app.jar
COPY servers/ktor/migrations/ ./migrations/
RUN addgroup -S quietmetrix && adduser -S -G quietmetrix quietmetrix
USER quietmetrix
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/v1/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]