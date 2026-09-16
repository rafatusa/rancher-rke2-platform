# syntax=docker/dockerfile:1

# ---- build stage ----------------------------------------------------------
# The official Gradle image is used rather than the Gradle wrapper so the image
# build has no dependency on a committed binary wrapper jar.
FROM gradle:8.10.2-jdk17 AS build

WORKDIR /workspace

# Dependency-defining files first so the Gradle cache survives source changes.
COPY settings.gradle build.gradle ./
RUN gradle --no-daemon dependencies --quiet || true

COPY src/ src/
RUN gradle --no-daemon clean bootJar -x test

# ---- runtime stage --------------------------------------------------------
FROM eclipse-temurin:17.0.12_7-jre-jammy AS runtime

RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --home /app --shell /usr/sbin/nologin app \
 && apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build --chown=app:app /workspace/build/libs/employee-api.jar /app/employee-api.jar

USER app

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl --fail --silent http://127.0.0.1:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/employee-api.jar"]
