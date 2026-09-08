plugins {
    `java-library`
}

description = "Persistence-neutral Query Filtering contracts and filterBy compilation"

dependencies {
    api(libs.jspecify)
    api(libs.spring.boot.core.starter)
    api(project(":modules:embedded-language"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}
