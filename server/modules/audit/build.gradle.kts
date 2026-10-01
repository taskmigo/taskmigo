plugins {
    `java-library`
}

description = "Taskmigo synchronous entity audit logging"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(project(":testing:architecture"))

    implementation(platform(libs.spring.boot.bom))
    api(project(":modules:foundation:core"))
    implementation(project(":modules:database"))

    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
}
