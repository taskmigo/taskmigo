plugins {
    `java-library`
}

description = "Taskmigo durable audit log capability"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)

    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))
    api(project(":modules:foundation:core"))

    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)
    implementation(libs.spring.modulith.events.api)
    implementation(libs.spring.modulith.events.jobrunr)

    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":testing:architecture"))
}
