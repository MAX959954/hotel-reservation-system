# ---- Build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# curl is only for the HEALTHCHECK below — everything else here needs just the JRE.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system app && adduser --system --ingroup app app \
    && mkdir -p /app/uploads && chown -R app:app /app

COPY --from=build --chown=app:app /app/build/libs/*-SNAPSHOT.jar app.jar

USER app

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
