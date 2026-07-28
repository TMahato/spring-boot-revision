# =============================================================================
# authService — multi-stage build. See notes/chapter-7 §3.
# =============================================================================

# ---- Stage 1: build ------------------------------------------------------
# Needs the full JDK + Maven. None of this survives into the final image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# pom.xml alone first: this layer (and the dependency download) stays cached
# until the pom actually changes, so editing Java doesn't re-resolve the tree.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests are skipped deliberately: they'd need a live MySQL/Kafka, which a build
# container has no access to. Run them in CI before building the image.
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: runtime ----------------------------------------------------
# JRE only — no compiler, no Maven, no .m2 cache. ~250MB instead of ~900MB.
FROM eclipse-temurin:21-jre

# curl is for the compose healthcheck; the JRE image ships without it, and a
# healthcheck referencing a missing binary reports unhealthy forever (§7.2).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Don't run as root (§3.3).
RUN useradd -r -u 1001 -m -d /home/appuser appuser
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser

# Documentation only — publishing happens in docker-compose.yml (§3.5).
EXPOSE 8080

# MaxRAMPercentage, not -Xmx: it adapts when the container's memory limit
# changes, a hardcoded heap size does not (§3.4).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
