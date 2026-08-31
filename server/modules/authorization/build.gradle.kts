plugins {
    `java-library`
}

description = "API ACL policies, reusable statements, and Role-based authorization"

dependencies {
    api(libs.jspecify)
    api(libs.spring.modulith.starter.core)
    implementation(project(":modules:foundation"))
    implementation(project(":modules:organization"))
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.data.jpa)

    testImplementation(libs.spring.boot.starter.test)
}
