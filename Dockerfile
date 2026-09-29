FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
COPY docs/openapi.yml ./docs/openapi.yml
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system jackpot && useradd --system --gid jackpot jackpot
COPY --from=build --chown=jackpot:jackpot /build/target/jackpot-service-1.0.0-SNAPSHOT.jar app.jar
USER jackpot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
