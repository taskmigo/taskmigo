import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    java
    id("taskmigo.java-base")
    id("org.springframework.boot")
}

val taskmigoCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    // Spring Framework 7 inspects its JSpecify annotations while resolving runtime metadata.
    // Keep the source-facing dependency compile-only, but package the annotation classes in
    // executable Boot artifacts so application startup does not fail with NoClassDefFoundError.
    add("runtimeOnly", taskmigoCatalog.findLibrary("jspecify").get())
    add("compileOnly", platform(taskmigoCatalog.findLibrary("spring-modulith-bom").get()))
    add("compileOnly", taskmigoCatalog.findLibrary("spring-modulith-starter-core").get())
    add("testImplementation", platform(taskmigoCatalog.findLibrary("spring-modulith-bom").get()))
    add("testImplementation", taskmigoCatalog.findLibrary("spring-modulith-starter-test").get())
}
