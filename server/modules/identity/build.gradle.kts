plugins {
    `java-library`
}

description = "Taskmigo Identity bounded context and Access Control integration"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    annotationProcessor(platform(libs.spring.boot.bom))
    annotationProcessor(libs.hibernate.processor)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(project(":testing:architecture"))

    implementation(platform(libs.spring.boot.bom))
    api(project(":modules:foundation:core"))
    implementation(project(":modules:audit"))
    api(project(":modules:query"))
    api(project(":modules:access-control"))
    implementation(project(":modules:database"))
    implementation(project(":modules:jpa-query"))
    implementation(project(":modules:language"))

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)
    implementation(libs.spring.boot.core.starter)
    implementation(platform(libs.guava.bom))
    implementation(libs.guava)

    testImplementation(libs.spring.boot.starter.test)
}
