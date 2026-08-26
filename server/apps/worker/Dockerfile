FROM eclipse-temurin:26.0.2_10-jdk-noble@sha256:00123cb1834b68c2957bd93506cf7011c38bcdca2d122dd2b1ecbf4ea056cae8 AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY modules/database/build.gradle.kts modules/database/build.gradle.kts
COPY modules/organization/build.gradle.kts modules/organization/build.gradle.kts
COPY modules/project/build.gradle.kts modules/project/build.gradle.kts
COPY apps/web/build.gradle.kts apps/web/build.gradle.kts
COPY apps/worker/build.gradle.kts apps/worker/build.gradle.kts

RUN chmod +x gradlew && ./gradlew --no-daemon :apps:worker:dependencies

COPY modules/database/src modules/database/src
COPY modules/organization/src modules/organization/src
COPY modules/project/src modules/project/src
COPY apps/worker/src apps/worker/src

RUN ./gradlew --no-daemon :apps:worker:bootJar \
    && cp apps/worker/build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:26.0.2_10-jre-noble@sha256:22e4987440fe837f31150df046a5bcb88c118566d1168de7b0307497e196154a

RUN groupadd --system --gid 10001 taskmigo \
    && useradd --system --uid 10001 --gid taskmigo --home-dir /app --shell /usr/sbin/nologin taskmigo

WORKDIR /app

COPY --from=build --chown=taskmigo:taskmigo /workspace/app.jar app.jar

USER 10001:10001

CMD ["java", "-jar", "/app/app.jar"]
