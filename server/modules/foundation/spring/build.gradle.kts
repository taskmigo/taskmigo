plugins {
    `java-library`
}

description = "Taskmigo Spring Boot foundation and shared runtime defaults"

dependencies {
    api(project(":modules:foundation:core"))
    api(platform(libs.spring.boot.bom))
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.security:spring-security-crypto")

    testImplementation(libs.spring.boot.starter.test)
}
