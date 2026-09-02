# syntax=docker/dockerfile:1

FROM eclipse-temurin:26.0.2_10-jdk-noble@sha256:00123cb1834b68c2957bd93506cf7011c38bcdca2d122dd2b1ecbf4ea056cae8 AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY modules/foundation/build.gradle.kts modules/foundation/build.gradle.kts
COPY modules/database/build.gradle.kts modules/database/build.gradle.kts
COPY modules/auth/build.gradle.kts modules/auth/build.gradle.kts
COPY apps/bootstrap/build.gradle.kts apps/bootstrap/build.gradle.kts
COPY apps/web/build.gradle.kts apps/web/build.gradle.kts
COPY apps/worker/build.gradle.kts apps/worker/build.gradle.kts

RUN chmod +x gradlew \
    && ./gradlew --no-daemon --parallel \
        :apps:bootstrap:dependencies \
        :apps:web:dependencies \
        :apps:worker:dependencies

COPY modules/foundation/src modules/foundation/src
COPY modules/database/src modules/database/src
COPY modules/auth/src modules/auth/src
COPY apps/bootstrap/src apps/bootstrap/src
COPY apps/web/src apps/web/src
COPY apps/worker/src apps/worker/src

RUN ./gradlew --no-daemon --parallel \
        :apps:bootstrap:bootJar \
        :apps:web:bootJar \
        :apps:worker:bootJar \
    && mkdir -p /workspace/images \
    && cp apps/bootstrap/build/libs/*.jar /workspace/images/bootstrap.jar \
    && cp apps/web/build/libs/*.jar /workspace/images/web.jar \
    && cp apps/worker/build/libs/*.jar /workspace/images/worker.jar

FROM eclipse-temurin:26.0.2_10-jre-alpine-3.24@sha256:c4a22bec4f4368636abb9b6fe2b2350fd7fae1ec0d3bf43fcaae1be720c3bbd1 AS runtime

RUN addgroup -S -g 10001 taskmigo \
    && adduser -S -D -H -u 10001 -G taskmigo taskmigo

WORKDIR /app

FROM runtime AS bootstrap

COPY --from=build --chown=taskmigo:taskmigo /workspace/images/bootstrap.jar app.jar

USER 10001:10001

CMD ["java", "-jar", "/app/app.jar"]

FROM runtime AS web

RUN mkdir -p /var/lib/taskmigo \
    && chown taskmigo:taskmigo /var/lib/taskmigo

COPY --from=build --chown=taskmigo:taskmigo /workspace/images/web.jar app.jar

ENV TASKMIGO_OAUTH_SIGNING_KEY_FILE=/var/lib/taskmigo/oauth-signing-key.pem

USER 10001:10001
EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar"]

FROM runtime AS worker

COPY --from=build --chown=taskmigo:taskmigo /workspace/images/worker.jar app.jar

USER 10001:10001

CMD ["java", "-jar", "/app/app.jar"]
