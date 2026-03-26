# ─── Stage 1: Build ───────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Cache dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline -q 2>/dev/null || true

COPY src ./src
RUN mvn package -DskipTests -q --no-transfer-progress

# ─── Stage 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Security: non-root user
RUN addgroup -S amenbank && adduser -S amenbank -G amenbank

# Copy jar
COPY --from=builder /build/target/amenbank-backend-*.jar app.jar

# Upload directory
RUN mkdir -p /data/uploads && chown amenbank:amenbank /data/uploads
VOLUME ["/data/uploads"]

# Logs directory
RUN mkdir -p /app/logs && chown amenbank:amenbank /app/logs

USER amenbank

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=5 \
  CMD wget -qO- http://localhost:8080/api/v1/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
