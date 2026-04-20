# syntax=docker/dockerfile:1.7

# --- Build stage -----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Warm the Gradle dependency cache before copying source so that later edits
# to src/ don't invalidate the deps layer.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src src
RUN ./gradlew --no-daemon bootJar

# --- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home /app app

COPY --from=build /workspace/build/libs/knowledge-bot-0.0.1-SNAPSHOT.jar /app/app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
