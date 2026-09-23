# Multi-stage build: compile with Maven, run on a slim JRE.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S opsmind && adduser -S opsmind -G opsmind
COPY --from=build /build/target/opsmind-core-*.jar app.jar
USER opsmind
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
