# syntax=docker/dockerfile:1

# ---------- Build stage ----------
# JDK 17 matches <java.version> in pom.xml; the Maven wrapper pins the project's Maven version (3.10.0).
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

# The cache mount keeps the Maven distribution and ~/.m2 dependencies between builds.
# save all the dependencies that download from maven so no need to download it again.
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -q package -DskipTests

# Split the fat jar into layers so dependency layers stay cached when only app code changes.
RUN cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Security: Run the application as a non-root user to minimize privileges
RUN addgroup -S spring && adduser -S -G spring spring

# Least-frequently changing layers first.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER spring
EXPOSE 8080


ENTRYPOINT ["java", "-jar", "application.jar"]
