plugins {
    id("taskmigo.spring-module")
    id("taskmigo.architecture-test")
}

description = "Taskmigo identity resources, persistence, and authorization integration"

dependencies {
    implementation(platform(libs.spring.boot.bom))
    api(project(":modules:foundation"))
    api(project(":modules:query"))
    api(project(":modules:authorization"))
    implementation(project(":modules:language"))

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.security.oauth2.authorization.server)
    implementation(platform(libs.guava.bom))
    implementation(libs.guava)

    testImplementation(libs.spring.boot.starter.test)
}
