FROM gradle:9.6.1-jdk26 AS build
WORKDIR /workspace
COPY . .
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:26-jre
RUN useradd --system --uid 10001 --create-home spring
USER spring
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
