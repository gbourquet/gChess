# ============================================================
# Single-Stage Dockerfile for gChess Backend
# ============================================================
#
# BUILD APPROACH: "Write Once, Run Anywhere"
#
# This Dockerfile expects a pre-compiled JAR file.
# Build workflow (local, CI, production):
#   1. ./gradlew build          (generates jOOQ + runs tests + creates JAR)
#   2. docker build -t gchess . (packages JAR into Docker image)
#   3. docker run gchess        (runs the application)
#
# WHY NOT MULTI-STAGE?
# - Testcontainers (jOOQ generation) requires Docker-in-Docker
# - Gradle cache is more efficient natively than in Docker layers
# - Same workflow everywhere: dev, CI, production
# - Easier debugging (build errors vs runtime errors are separated)
#
# ============================================================

FROM eclipse-temurin:21-jre-alpine

# Install wget for healthcheck
RUN apk add --no-cache wget

# Create non-root user for security
RUN addgroup -S gchess && adduser -S gchess -G gchess

WORKDIR /app

# Copy pre-compiled fat JAR (must be built with ./gradlew build before docker build)
# Shadow plugin creates gChess-VERSION-all.jar with all dependencies included
COPY build/libs/gChess-*-all.jar /app/gchess.jar

# Change ownership to non-root user
RUN chown -R gchess:gchess /app

# Switch to non-root user
USER gchess

# Expose application port
EXPOSE 8080

# Health check for orchestration tools (Docker, Kubernetes, load balancers)
# Checks /health endpoint every 30 seconds
# Starts checking after 60 seconds (gives time for migrations)
# Allows 3 retries with 10 second timeout
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --timeout=5 -O /dev/null http://localhost:8080/health || exit 1

# JVM optimization flags:
# -Xmx512m: Maximum heap size 512MB (adjust based on available memory)
# -XX:+UseG1GC: Use G1 garbage collector (good for low-latency applications)
# -XX:MaxGCPauseMillis=200: Target max GC pause time
# -XX:+UseContainerSupport: Respect container memory limits
ENTRYPOINT ["java", \
    "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+UseContainerSupport", \
    "-jar", \
    "/app/gchess.jar"]

# Environment variables (can be overridden at runtime):
# - PORT: Server port (default: 8080)
# - ENVIRONMENT: local|test|prod (default: local)
# - DATABASE_URL: JDBC connection string
# - DATABASE_USER: Database username
# - DATABASE_PASSWORD: Database password
# - JWT_SECRET: JWT signing secret (REQUIRED in production)
# - CORS_ORIGINS: Comma-separated list of allowed origins
# - LOG_LEVEL: DEBUG|INFO|WARN|ERROR (default: INFO)