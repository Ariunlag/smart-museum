FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/artinfo-service-1.0.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]