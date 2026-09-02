# syntax=docker/dockerfile:1

FROM eclipse-temurin:26.0.2_10-jdk-noble@sha256:00123cb1834b68c2957bd93506cf7011c38bcdca2d122dd2b1ecbf4ea056cae8 AS build

WORKDIR /workspace

COPY --chmod=755 gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY modules ./modules
COPY apps ./apps

RUN ./gradlew --no-daemon --parallel \
        :apps:bootstrap:dependencies \
        :apps:web:dependencies \
        :apps:worker:dependencies

RUN ./gradlew --no-daemon --parallel \
        :apps:bootstrap:bootJar \
        :apps:web:bootJar \
        :apps:worker:bootJar \
    && mkdir -p /workspace/images \
    && cp apps/bootstrap/build/libs/bootstrap.jar /workspace/images/bootstrap.jar \
    && cp apps/web/build/libs/web.jar /workspace/images/web.jar \
    && cp apps/worker/build/libs/worker.jar /workspace/images/worker.jar

FROM eclipse-temurin:26.0.2_10-jre-alpine-3.24@sha256:c4a22bec4f4368636abb9b6fe2b2350fd7fae1ec0d3bf43fcaae1be720c3bbd1 AS runtime

RUN addgroup -S -g 10001 taskmigo \
    && adduser -S -D -H -u 10001 -G taskmigo taskmigo

WORKDIR /app

FROM runtime AS bootstrap

COPY --from=build --chown=taskmigo:taskmigo /workspace/images/bootstrap.jar .

USER 10001:10001

CMD ["java", "-jar", "/app/bootstrap.jar"]

FROM runtime AS web

RUN mkdir -p /var/lib/taskmigo \
    && chown taskmigo:taskmigo /var/lib/taskmigo

COPY --from=build --chown=taskmigo:taskmigo /workspace/images/web.jar .

ENV TASKMIGO_OAUTH_SIGNING_KEY_FILE=/var/lib/taskmigo/oauth-signing-key.pem

USER 10001:10001
EXPOSE 8080

CMD ["java", "-jar", "/app/web.jar"]

FROM runtime AS worker

COPY --from=build --chown=taskmigo:taskmigo /workspace/images/worker.jar .

USER 10001:10001

CMD ["java", "-jar", "/app/worker.jar"]
