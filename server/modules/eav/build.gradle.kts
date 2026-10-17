plugins {
    `java-library`
}

description = "Taskmigo generic versioned typed EAV capability"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    annotationProcessor(platform(libs.spring.boot.bom))
    annotationProcessor(libs.hibernate.processor)

    implementation(platform(libs.spring.boot.bom))
    api(project(":modules:query"))
    api(project(":modules:jpa-query"))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.core.starter)
    implementation(libs.jackson.databind)

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
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly(libs.postgresql.driver)
}
