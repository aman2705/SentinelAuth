FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy the Gradle build JAR
COPY build/libs/authservice-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 9898

# Add health check script
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD curl -f http://localhost:9898/auth/v1/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]