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
    implementation(project(":modules:foundation:spring"))
    implementation(project(":modules:query"))
    implementation(project(":modules:audit"))
    implementation(project(":modules:access-control"))
    implementation(project(":modules:database"))
    implementation(project(":modules:identity"))
    runtimeOnly(libs.jspecify)
    runtimeOnly(libs.postgresql.driver)
    implementation(libs.spring.boot.core.starter)

    testImplementation(libs.spring.boot.starter.test)
}
