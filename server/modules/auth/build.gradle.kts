plugins {
    `java-library`
}

description = "Authentication and authorization"

dependencies {
    api(libs.jspecify)
    api(libs.spring.modulith.starter.core)
    api(project(":modules:foundation"))

    implementation(project(":modules:api"))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.oauth2.authorization.server)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.springdoc.openapi.starter.webmvc.scalar)
    implementation(platform(libs.guava.bom))
    implementation(libs.guava)

    testImplementation(libs.spring.boot.starter.test)
}
