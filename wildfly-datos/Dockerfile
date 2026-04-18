FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM quay.io/wildfly/wildfly:32.0.1.Final-jdk21
USER root
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar /opt/jboss/wildfly/postgresql.jar
COPY docker/configure-datasources.cli /opt/jboss/wildfly/configure-datasources.cli
RUN chown jboss:root /opt/jboss/wildfly/postgresql.jar /opt/jboss/wildfly/configure-datasources.cli && \
    chmod 640 /opt/jboss/wildfly/postgresql.jar /opt/jboss/wildfly/configure-datasources.cli && \
    /opt/jboss/wildfly/bin/jboss-cli.sh --file=/opt/jboss/wildfly/configure-datasources.cli && \
    chown -R jboss:root /opt/jboss/wildfly/standalone && \
    rm -f /opt/jboss/wildfly/configure-datasources.cli
COPY --from=builder --chown=jboss:root /workspace/target/wildfly-datos.war /opt/jboss/wildfly/standalone/deployments/ROOT.war
USER jboss
