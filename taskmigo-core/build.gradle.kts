plugins {
    `java-library`
}

description = "Shared Taskmigo domain and persistence modules"

dependencies {
    api("org.springframework.modulith:spring-modulith-starter-core")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")
}
