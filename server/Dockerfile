# syntax=docker/dockerfile:1

FROM eclipse-temurin:26.0.2_10-jdk-noble@sha256:00123cb1834b68c2957bd93506cf7011c38bcdca2d122dd2b1ecbf4ea056cae8 AS build

WORKDIR /workspace

COPY --chmod=755 gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY modules/foundation/build.gradle.kts modules/foundation/build.gradle.kts
COPY modules/database/build.gradle.kts modules/database/build.gradle.kts
COPY modules/auth/build.gradle.kts modules/auth/build.gradle.kts
COPY apps/bootstrap/build.gradle.kts apps/bootstrap/build.gradle.kts
COPY apps/web/build.gradle.kts apps/web/build.gradle.kts
COPY apps/worker/build.gradle.kts apps/worker/build.gradle.kts

RUN --mount=type=cache,id=taskmigo-gradle,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon --parallel \
        :apps:bootstrap:dependencies \
        :apps:web:dependencies \
        :apps:worker:dependencies

COPY modules/foundation/src modules/foundation/src
COPY modules/database/src modules/database/src
COPY modules/auth/src modules/auth/src
COPY apps/bootstrap/src apps/bootstrap/src
COPY apps/web/src apps/web/src
COPY apps/worker/src apps/worker/src

RUN --mount=type=cache,id=taskmigo-gradle,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon --parallel \
        :apps:bootstrap:bootJar \
        :apps:web:bootJar \
        :apps:worker:bootJar \
    && for taskmigo_app in bootstrap web worker; do \
        java -Djarmode=tools -jar "apps/${taskmigo_app}/build/libs/${taskmigo_app}.jar" \
            extract --layers --launcher --destination "extracted/${taskmigo_app}"; \
    done

FROM eclipse-temurin:26.0.2_10-jre-alpine-3.24@sha256:c4a22bec4f4368636abb9b6fe2b2350fd7fae1ec0d3bf43fcaae1be720c3bbd1 AS runtime

RUN addgroup -S -g 10001 taskmigo \
    && adduser -S -D -H -u 10001 -G taskmigo taskmigo \
    && mkdir -p /app /var/lib/taskmigo \
    && chown -R taskmigo:taskmigo /app /var/lib/taskmigo

WORKDIR /app

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

FROM runtime AS bootstrap

COPY --from=build --chown=taskmigo:taskmigo /workspace/extracted/bootstrap/dependencies/ ./
COPY --from=build --chown=taskmigo:taskmigo /workspace/extracted/bootstrap/spring-boot-loader/ ./
COPY --from=build --chown=taskmigo:taskmigo /workspace/extracted/bootstrap/snapshot-dependencies/ ./
COPY --from=build --chown=taskmigo:taskmigo /workspace/extracted/bootstrap/application/ ./

USER 10001:10001

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

FROM runtime AS web

COPY --from=build --chown=taskmigo:taskmigo /workspace/extracted/web/dependencies/ ./
COPY --from=build --chown=taskmigo:taskmigo /workspace/extracted/web/spring-boot-loader/ ./
COPY --from=build --chown=taskmigo:taskmigo /workspace/extracted/web/snapshot-dependencies/ ./
COPY --from=build --chown=taskmigo:taskmigo /workspace/extracted/web/application/ ./

ENV TASKMIGO_OAUTH_SIGNING_KEY_FILE=/var/lib/taskmigo/oauth-signing-key.pem

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

FROM runtime AS worker

COPY --from=build --chown=taskmigo:taskmigo /workspace/extracted/worker/dependencies/ ./
COPY --from=build --chown=taskmigo:taskmigo /workspace/extracted/worker/spring-boot-loader/ ./
COPY --from=build --chown=taskmigo:taskmigo /workspace/extracted/worker/snapshot-dependencies/ ./
COPY --from=build --chown=taskmigo:taskmigo /workspace/extracted/worker/application/ ./

USER 10001:10001

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
