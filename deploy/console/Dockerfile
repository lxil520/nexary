FROM apache/skywalking-java-agent:9.5.0-java17 AS skywalking-agent
FROM eclipse-temurin:21-jdk
WORKDIR /opt/nexary
COPY deploy/console/build/app.jar ./app.jar
COPY --from=skywalking-agent /skywalking/agent /opt/skywalking/agent
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/nexary/app.jar", "--server.port=8080"]
