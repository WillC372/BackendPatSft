FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 aquaassistant
COPY --from=build /app/target/aqua-assistant-backend-1.0.0.jar app.jar
USER aquaassistant
EXPOSE 8080
# Sized for 512 MB instances (Render free plan). The PDF reports and the AI models now run in the AI service.
ENV JAVA_OPTS="-Xmx256m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
