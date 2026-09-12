plugins {
    `java-library`
}

description = "Shared database configuration, migrations, and PostgreSQL runtime support"

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter.data.jpa)

    runtimeOnly(libs.postgresql.driver)
}
