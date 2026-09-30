plugins {
    java
    alias(libs.plugins.spring.boot)
}

tasks.bootJar {
    archiveFileName = "worker.jar"
}

description = "Taskmigo background worker application"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(project(":testing:architecture"))

    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))
    implementation(project(":modules:audit"))
    implementation(project(":modules:database"))
    runtimeOnly(libs.jspecify)
    implementation(libs.spring.modulith.starter.jobrunr)
    implementation(libs.jobrunr)
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.jackson)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.modulith.events.jobrunr)
    testImplementation(libs.jobrunr)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.boot.starter.flyway)
    testImplementation(libs.flyway.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}
