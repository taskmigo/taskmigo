plugins {
    java
    id("org.springframework.boot")
}

description = "Taskmigo background worker application"

dependencies {
    implementation(libs.jspecify)
    implementation(project(":organization"))
    implementation(project(":project"))
    implementation(libs.spring.boot.core.starter)

    runtimeOnly(libs.postgresql.driver)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.modulith.starter.test)
}
