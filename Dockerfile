# Build stage
FROM eclipse-temurin:21-jdk as builder
WORKDIR /app
COPY . .
RUN apt-get update && apt-get install -y maven && \
    mvn -B -ntp clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
