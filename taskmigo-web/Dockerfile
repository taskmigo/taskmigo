FROM eclipse-temurin:26-jdk-noble AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY taskmigo-core/build.gradle.kts taskmigo-core/build.gradle.kts
COPY taskmigo-web/build.gradle.kts taskmigo-web/build.gradle.kts
COPY taskmigo-worker/build.gradle.kts taskmigo-worker/build.gradle.kts

RUN chmod +x gradlew && ./gradlew --no-daemon :taskmigo-web:dependencies

COPY taskmigo-core/src taskmigo-core/src
COPY taskmigo-web/src taskmigo-web/src

RUN ./gradlew --no-daemon :taskmigo-web:bootJar \
    && cp taskmigo-web/build/libs/taskmigo-web-0.0.1-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:26-jre-noble

RUN groupadd --system --gid 10001 taskmigo \
    && useradd --system --uid 10001 --gid taskmigo --home-dir /app --shell /usr/sbin/nologin taskmigo

WORKDIR /app

COPY --from=build --chown=taskmigo:taskmigo /workspace/app.jar app.jar

USER 10001:10001
EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar"]

