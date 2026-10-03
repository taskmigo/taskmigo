plugins {
    `java-library`
}

description = "Taskmigo shared Jackson serialization policy"

dependencies {
    api(platform(libs.spring.boot.bom))
    api(libs.jackson.databind)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-jackson")

    testImplementation(libs.spring.boot.starter.jackson)
    testImplementation(libs.spring.boot.starter.test)
}
