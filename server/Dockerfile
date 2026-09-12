# syntax=docker/dockerfile:1

FROM eclipse-temurin:26.0.2_10-jdk-noble@sha256:00123cb1834b68c2957bd93506cf7011c38bcdca2d122dd2b1ecbf4ea056cae8 AS build

WORKDIR /workspace

COPY --chmod=755 gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY build-logic build-logic
COPY modules/foundation/build.gradle.kts modules/foundation/build.gradle.kts
COPY modules/language/build.gradle.kts modules/language/build.gradle.kts
COPY modules/query/build.gradle.kts modules/query/build.gradle.kts
COPY modules/authorization/build.gradle.kts modules/authorization/build.gradle.kts
COPY modules/database/build.gradle.kts modules/database/build.gradle.kts
COPY modules/identity/build.gradle.kts modules/identity/build.gradle.kts
COPY benchmarks/authorization/build.gradle.kts benchmarks/authorization/build.gradle.kts
COPY apps/bootstrap/build.gradle.kts apps/bootstrap/build.gradle.kts
COPY apps/web/build.gradle.kts apps/web/build.gradle.kts
COPY apps/worker/build.gradle.kts apps/worker/build.gradle.kts

RUN --mount=type=cache,id=taskmigo-gradle,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon --parallel \
        :apps:bootstrap:dependencies \
        :apps:web:dependencies \
        :apps:worker:dependencies

COPY modules/foundation/src modules/foundation/src
COPY modules/language/src modules/language/src
COPY modules/query/src modules/query/src
COPY modules/authorization/src modules/authorization/src
COPY modules/database/src modules/database/src
COPY modules/identity/src modules/identity/src
COPY benchmarks/authorization/src benchmarks/authorization/src
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

FROM eclipse-temurin:26.0.2_10-jdk-alpine-3.24@sha256:e5b0a876436e5d1c8ff84dfd091cb530b83a9ed4273317d24acb673695bb7e0c AS runtime-builder

RUN jlink \
        --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.jfr,jdk.management,jdk.unsupported,jdk.zipfs \
        --strip-debug \
        --no-man-pages \
        --no-header-files \
        --compress=zip-6 \
        --output /opt/taskmigo-jre

FROM alpine:3.24@sha256:28bd5fe8b56d1bd048e5babf5b10710ebe0bae67db86916198a6eec434943f8b AS runtime

RUN apk add --no-cache \
        ca-certificates=20260611-r0 \
        p11-kit-trust=0.26.2-r0 \
        tzdata=2026c-r0

COPY --from=runtime-builder /opt/taskmigo-jre /opt/java

RUN addgroup -S -g 10001 taskmigo \
    && adduser -S -D -H -u 10001 -G taskmigo taskmigo \
    && mkdir -p /app /var/lib/taskmigo \
    && chown -R taskmigo:taskmigo /app /var/lib/taskmigo

WORKDIR /app

ENV JAVA_HOME=/opt/java \
    PATH=/opt/java/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

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
