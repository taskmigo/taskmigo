import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("worker.jar")
}

description = "Taskmigo background worker application"

dependencies {
    implementation(libs.jspecify)
    implementation(project(":modules:database"))
    implementation(project(":modules:auth"))
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.jackson)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.modulith.starter.test)
}
