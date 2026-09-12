plugins {
    `java-library`
}

description = "Taskmigo Query Filtering contracts, validation, and filterBy compilation"

dependencies {
    api(libs.jspecify)
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    implementation(platform(libs.spring.boot.bom))
    api(libs.spring.boot.core.starter)
    api(project(":modules:language"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}
