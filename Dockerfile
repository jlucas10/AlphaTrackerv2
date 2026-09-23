# --- Build stage ---
# Full JDK + Maven only exist in this layer; none of it ships in the final image.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the wrapper and pom first so a source-only change doesn't invalidate
# the dependency-download layer cache.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

COPY src src

# Tests need a live Postgres, which doesn't exist at image-build time - they
# already run separately (CI / `./mvnw test` locally) before this ever gets
# built, so skipping here isn't skipping verification, just skipping a build
# step that can't succeed in this environment anyway.
RUN ./mvnw -q package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Railway (and most PaaS platforms) inject PORT at runtime rather than fixing
# 8080 - Spring Boot reads server.port from this env var automatically.
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT}"]
