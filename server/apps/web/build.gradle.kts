plugins {
    java
    id("org.springframework.boot")
}

description = "Taskmigo HTTP and OAuth application"

dependencies {
    implementation(libs.jspecify)
    implementation(project(":modules:foundation"))
    implementation(project(":modules:database"))
    implementation(project(":modules:api-foundation"))
    implementation(project(":modules:auth"))
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.oauth2.authorization.server)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.springdoc.openapi.starter.webmvc.scalar)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.spring.boot.starter.flyway)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

val verifyWebCompositionRoot = tasks.register("verifyWebCompositionRoot") {
    group = "verification"
    description = "Ensures feature HTTP controllers are owned by server modules instead of apps/web"

    doLast {
        val forbiddenImports = listOf(
            "import io.taskmigo.api.v0.ApiV0Controller;",
            "import org.springframework.web.bind.annotation.RestController;",
        )
        val controllers = fileTree("src/main/java") {
            include("**/*.java")
        }.files
            .filter { file -> forbiddenImports.any { forbidden -> file.readText().contains(forbidden) } }
            .map { it.relativeTo(projectDir).invariantSeparatorsPath }
            .sorted()

        check(controllers.isEmpty()) {
            "apps/web is a composition root; move HTTP controllers into their owning modules: ${controllers.joinToString()}"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyWebCompositionRoot)
}
