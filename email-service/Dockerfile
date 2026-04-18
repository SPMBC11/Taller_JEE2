FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /opt/app
COPY --from=builder /app/target/email-service-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
