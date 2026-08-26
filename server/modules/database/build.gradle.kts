plugins {
    `java-library`
}

description = "Database bootstrap, Flyway migrations, and PostgreSQL runtime support"

dependencies {
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.postgresql)

    runtimeOnly(libs.postgresql.driver)
}
