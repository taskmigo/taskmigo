plugins {
    `java-library`
}

description = "Taskmigo shared Jackson serialization policy"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)

    api(platform(libs.spring.boot.bom))
    api(libs.jackson.databind)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-jackson")

    testImplementation(libs.spring.boot.starter.jackson)
    testImplementation(libs.spring.boot.starter.test)
}
