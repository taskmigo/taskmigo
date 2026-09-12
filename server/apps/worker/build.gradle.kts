import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("taskmigo.spring-application")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("worker.jar")
}

description = "Taskmigo background worker application"

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":modules:database"))
    implementation(project(":modules:identity"))
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.jackson)

    testImplementation(libs.spring.boot.starter.test)
}
