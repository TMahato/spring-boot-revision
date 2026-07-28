# =============================================================================
# expenseService — multi-stage build. See notes/chapter-7 §3.
# Same shape as authService/userService; only the port differs.
#
# The reference Dockerfile copied a pre-built build/libs/*.jar, which means the
# image only builds if you remember to run gradle first — and silently ships a
# stale jar if you forget. This builds from source inside the image.
# =============================================================================

# ---- Stage 1: build ------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# pom.xml first so the dependency layer survives source changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: runtime ----------------------------------------------------
# JRE, not the reference's full openjdk:21 image — roughly a third the size.
FROM eclipse-temurin:21-jre

# curl for the compose healthcheck (§7.2).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -r -u 1001 -m -d /home/appuser appuser
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser

EXPOSE 9820

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
