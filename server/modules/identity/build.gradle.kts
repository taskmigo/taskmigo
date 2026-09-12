plugins {
    `java-library`
}

description = "Taskmigo identity resources, persistence, and authorization integration"

dependencies {
    api(libs.jspecify)
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
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
    testImplementation(libs.spring.modulith.starter.test)
}
