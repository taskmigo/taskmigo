plugins {
    id("org.springframework.boot")
}

description = "Taskmigo background worker application"

dependencies {
    implementation(project(":taskmigo-core"))
    implementation("org.springframework.boot:spring-boot-starter")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
}
