plugins {
    `java-library`
}

description = "Projects, memberships, authorization, and project history"

dependencies {
    api(libs.jspecify)
    api(libs.spring.modulith.starter.core)
    api(project(":modules:foundation"))
    api(project(":modules:organization"))
    implementation(project(":modules:authorization"))

    implementation(libs.jackson.databind)
    implementation(libs.spring.boot.starter.data.jpa)
}
