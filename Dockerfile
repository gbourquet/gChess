# ============================================================
# Multi-Stage Dockerfile for gChess Backend
# ============================================================
#
# Stage 1 (builder) : compile le fat JAR avec Gradle
# Stage 2 (runtime) : image légère JRE pour l'exécution
#
# Les sources jOOQ sont committées dans src/main/generated/
# et n'ont pas besoin d'être régénérées pendant le build.
# Pour les régénérer en local : ./gradlew generateJooq
#
# ============================================================

# ============================================================
# Stage 1 : Builder
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copier les fichiers Gradle en premier pour bénéficier du cache Docker
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY buildSrc/ buildSrc/

# Télécharger les dépendances (layer mis en cache si build.gradle.kts ne change pas)
RUN ./gradlew dependencies --no-daemon --quiet 2>/dev/null || true

# Copier le code source (y compris src/main/generated/ avec les sources jOOQ committées)
COPY src/ src/

# Compiler et générer le fat JAR
# - generateJooq et setupJooqDatabase sont exclus : les sources sont dans src/main/generated/
# - Les tests sont exclus : ils sont exécutés en CI séparément
RUN ./gradlew shadowJar --no-daemon \
    -x generateJooq \
    -x setupJooqDatabase \
    -x test \
    -x unitTest \
    -x integrationTest \
    -x architectureTest

# ============================================================
# Stage 2 : Runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine

# wget pour le healthcheck
RUN apk add --no-cache wget

# Utilisateur non-root pour la sécurité
RUN addgroup -S gchess && adduser -S gchess -G gchess

WORKDIR /app

# Copier uniquement le JAR depuis le stage builder
COPY --from=builder /build/build/libs/gChess-*-all.jar /app/gchess.jar

RUN chown -R gchess:gchess /app
USER gchess

EXPOSE 8080

# Healthcheck (délai de 60s pour laisser le temps aux migrations Liquibase)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --timeout=5 -O /dev/null http://localhost:8080/health || exit 1

# Options JVM :
# -Xmx512m              : heap max 512 MB
# -XX:+UseG1GC          : GC G1 (faible latence)
# -XX:MaxGCPauseMillis=200 : cible de pause GC
# -XX:+UseContainerSupport : respecte les limites mémoire du container
ENTRYPOINT ["java", \
    "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+UseContainerSupport", \
    "-jar", \
    "/app/gchess.jar"]

# Variables d'environnement (à configurer dans Railway) :
# - PORT             : port d'écoute (défaut : 8080, Railway injecte automatiquement)
# - ENVIRONMENT      : local|test|prod (défaut : local)
# - DATABASE_URL     : JDBC URL PostgreSQL (ex: jdbc:postgresql://host:5432/db)
# - DATABASE_USER    : utilisateur PostgreSQL
# - DATABASE_PASSWORD: mot de passe PostgreSQL
# - JWT_SECRET       : secret HMAC256 pour signer les JWT (OBLIGATOIRE en prod)
# - CORS_ORIGINS     : origines autorisées, séparées par des virgules
# - LOG_LEVEL        : DEBUG|INFO|WARN|ERROR (défaut : INFO)
