FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 aquasense
COPY --from=build /app/target/aquasense-backend-1.0.0.jar app.jar
USER aquasense
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
