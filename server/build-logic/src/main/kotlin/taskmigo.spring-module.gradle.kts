import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    id("taskmigo.java-library")
}

val taskmigoCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("compileOnly", platform(taskmigoCatalog.findLibrary("spring-modulith-bom").get()))
    add("compileOnly", taskmigoCatalog.findLibrary("spring-modulith-starter-core").get())
    add("testImplementation", platform(taskmigoCatalog.findLibrary("spring-modulith-bom").get()))
    add("testImplementation", taskmigoCatalog.findLibrary("spring-modulith-starter-test").get())
}
