plugins {
    java
    alias(libs.plugins.spring.boot)
}

tasks.bootJar {
    archiveFileName = "worker.jar"
}

description = "Taskmigo background worker application"

dependencies {
    implementation(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.namastack)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(project(":testing:architecture"))

    implementation(platform(libs.spring.boot.bom))
    implementation(project(":modules:foundation:spring"))
    implementation(project(":modules:database"))
    implementation(project(":modules:audit"))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.jdbc)
    runtimeOnly(libs.jspecify)
    implementation(libs.spring.boot.core.starter)

    testImplementation(libs.spring.boot.starter.test)
}
