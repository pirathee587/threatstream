FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY common/pom.xml common/pom.xml
COPY event-collector-service/pom.xml event-collector-service/pom.xml
COPY threat-detection-service/pom.xml threat-detection-service/pom.xml
COPY alert-service/pom.xml alert-service/pom.xml
COPY demo-login-service/pom.xml demo-login-service/pom.xml
RUN mvn -q -DskipTests dependency:go-offline
COPY . .
ARG MODULE
RUN mvn -q -pl ${MODULE} -am package -DskipTests
FROM eclipse-temurin:21-jre
ARG MODULE
RUN groupadd -r threatstream && useradd -r -g threatstream -d /app -s /sbin/nologin threatstream
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/*.jar /app/app.jar
RUN chown -R threatstream:threatstream /app
USER threatstream:threatstream
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
