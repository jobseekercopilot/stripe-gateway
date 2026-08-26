FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B --no-transfer-progress clean verify

FROM eclipse-temurin:17-jre-alpine

# The runtime base currently ships OpenSSL 3.5.7-r0. Refresh only the runtime
# packages to Alpine's fixed CVE-2026-14456 build.
RUN apk add --no-cache --upgrade \
    libcrypto3=3.5.8-r0 \
    libssl3=3.5.8-r0 \
    openssl=3.5.8-r0
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN apk add --no-cache curl
EXPOSE 8100
ENTRYPOINT ["java", "-jar", "app.jar"]
