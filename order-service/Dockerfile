# Build context is the repo root (see docker-compose.yml) so the Maven reactor — root pom plus
# both modules' poms — is visible; only order-service's sources actually get compiled.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY order-service/pom.xml order-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml

# Warm the local repo cache from just the POMs, so editing source later doesn't bust this layer.
RUN mvn -q -pl order-service -am -DskipTests dependency:go-offline

COPY order-service/src order-service/src
COPY notification-service/src notification-service/src

RUN mvn -q -pl order-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# curl is needed for the docker-compose healthcheck against /actuator/health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/order-service/target/order-service.jar app.jar

EXPOSE 8006
ENTRYPOINT ["java", "-jar", "app.jar"]
