FROM eclipse-temurin:26-jre-alpine
COPY quarkus-app /app
WORKDIR /app
ENTRYPOINT ["java", "-Xmx256m", "-jar", "quarkus-run.jar"]
