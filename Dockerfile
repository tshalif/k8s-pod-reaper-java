FROM openjdk:11

ENV APP_HOME /app
WORKDIR $APP_HOME
COPY target/k8s-pod-reaper-1.0-SNAPSHOT-jar-with-dependencies.jar ./
COPY .env ./
ENTRYPOINT ["java", "-jar", "k8s-pod-reaper-1.0-SNAPSHOT-jar-with-dependencies.jar"]
