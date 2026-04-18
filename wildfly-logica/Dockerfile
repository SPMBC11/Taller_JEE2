FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM quay.io/wildfly/wildfly:32.0.1.Final-jdk21
COPY --from=builder /workspace/target/wildfly-logica.war /opt/jboss/wildfly/standalone/deployments/ROOT.war
