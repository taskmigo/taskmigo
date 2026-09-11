plugins {
    `java-library`
}

description = "Authorization resource persistence and query integration"

dependencies {
    api(libs.jspecify)
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))
    api(libs.spring.modulith.starter.core)
    api(project(":modules:foundation"))
    api(project(":modules:language"))
    api(project(":modules:query"))
    api(project(":modules:authorization"))

    implementation(project(":modules:identity"))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)
    implementation(libs.spring.boot.core.starter)
    implementation(platform(libs.guava.bom))
    implementation(libs.guava)

    testImplementation(libs.spring.boot.starter.test)
}
