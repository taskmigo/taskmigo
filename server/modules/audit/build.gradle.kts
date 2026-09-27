plugins {
    `java-library`
}

description = "Taskmigo durable audit log capability"

dependencies {
    api(project(":modules:foundation:core"))

    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.events.api)
    implementation(libs.namastack.outbox.api)
    implementation(libs.namastack.outbox.core)

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.boot.starter.test)
}
