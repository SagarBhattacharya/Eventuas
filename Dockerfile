# Stage 1: Build Spring Boot fat JAR
FROM gradle:9-jdk25 AS builder
WORKDIR /workspace

COPY settings.gradle.kts build.gradle.kts ./
RUN gradle dependencies --no-daemon || true

COPY src ./src
RUN gradle bootJar --no-daemon -x test

# Stage 2: Minimal non-root JRE runtime
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S eventuas && adduser -S eventuas -G eventuas
COPY --from=builder --chown=eventuas:eventuas /workspace/build/libs/*.jar app.jar

USER eventuas
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC"

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]