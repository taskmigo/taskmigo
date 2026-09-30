plugins {
    `java-library`
}

description = "Taskmigo durable entity audit logging"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    compileOnly(libs.spring.modulith.events.jobrunr)
    compileOnly(libs.jobrunr)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.jobrunr)
    testImplementation(project(":testing:architecture"))

    implementation(platform(libs.spring.boot.bom))
    api(project(":modules:foundation:core"))
    implementation(project(":modules:database"))

    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
}
