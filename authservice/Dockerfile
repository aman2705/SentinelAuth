FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# Copy the Gradle build JAR
COPY build/libs/authservice-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 9898

ENTRYPOINT ["java", "-jar", "app.jar"]