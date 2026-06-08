# syntax=docker/dockerfile:1

# --- build stage ---
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# --- runtime stage ---
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/todoai-*.jar app.jar
EXPOSE 8080
# MaxRAMPercentage caps heap so the JVM stays within the pinned 512MB Fly machine
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
