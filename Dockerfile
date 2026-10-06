FROM eclipse-temurin:26-jdk AS build

WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew
COPY src/main ./src/main
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:26-jre

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar ./app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
