plugins {
    java
    alias(libs.plugins.spring.boot)
}

tasks.bootJar {
    archiveFileName = "migration.jar"
}

description = "Taskmigo database migration application"

dependencies {
    implementation(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.namastack)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(project(":testing:architecture"))

    implementation(project(":modules:foundation:spring"))
    implementation(project(":modules:database"))
    implementation(project(":modules:audit"))
    implementation(project(":modules:access-control"))
    implementation(project(":modules:identity"))
    runtimeOnly(libs.jspecify)
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.postgresql)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.security.oauth2.authorization.server)
    implementation(libs.jackson.dataformat.yaml)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}
