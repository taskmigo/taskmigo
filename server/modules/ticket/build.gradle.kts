plugins {
    `java-library`
}

description = "Taskmigo Ticket bounded context"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    annotationProcessor(platform(libs.spring.boot.bom))
    annotationProcessor(libs.hibernate.processor)

    implementation(platform(libs.spring.boot.bom))
    implementation(project(":modules:jpa-query"))
    implementation(project(":modules:eav"))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.core.starter)

    api(project(":modules:foundation:core"))
    api(project(":modules:query"))
    api(project(":modules:authorization"))

    testImplementation(project(":modules:database"))
    testImplementation(project(":testing:architecture"))
    testImplementation(libs.archunit.junit5)
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.boot.starter.jdbc)
    testImplementation(libs.spring.boot.starter.flyway)
    testImplementation(libs.flyway.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.postgresql.driver)
}
