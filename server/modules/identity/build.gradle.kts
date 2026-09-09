plugins {
    `java-library`
}

description = "Taskmigo identity resources, persistence, and authorization integration"

dependencies {
    api(libs.jspecify)
    api(libs.spring.modulith.starter.core)
    api(project(":modules:foundation"))
    api(project(":modules:query"))
    api(project(":modules:authorization"))

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.oauth2.authorization.server)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(platform(libs.guava.bom))
    implementation(libs.guava)

    testImplementation(libs.spring.boot.starter.test)
}
