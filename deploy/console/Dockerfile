FROM eclipse-temurin:21-jdk
WORKDIR /opt/nexary
COPY deploy/console/build/app.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/nexary/app.jar", "--server.port=8080"]
