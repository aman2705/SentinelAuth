# Use non-Alpine image for better Kafka library compatibility
# Alpine uses musl libc which can cause issues with some native libraries
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy the Gradle build JAR
COPY build/libs/userservice-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 9810

# Add health check script
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD curl -f http://localhost:9810/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
