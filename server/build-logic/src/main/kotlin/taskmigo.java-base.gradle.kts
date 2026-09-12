import com.diffplug.gradle.spotless.SpotlessExtension
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.getByType

plugins {
    id("net.ltgt.errorprone")
    checkstyle
    id("com.diffplug.spotless")
}

val taskmigoCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

repositories {
    mavenCentral()
}

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

dependencies {
    add("testRuntimeOnly", taskmigoCatalog.findLibrary("junit-platform-launcher").get())
    add("errorprone", taskmigoCatalog.findLibrary("errorprone-core").get())
    add("errorprone", taskmigoCatalog.findLibrary("nullaway").get())

    if (pluginManager.hasPlugin("java-library")) {
        add("compileOnlyApi", taskmigoCatalog.findLibrary("jspecify").get())
    } else {
        add("compileOnly", taskmigoCatalog.findLibrary("jspecify").get())
    }
}

extensions.configure<SpotlessExtension> {
    java {
        shortenFullyQualifiedTypes()
        importOrder()
        removeUnusedImports()
        cleanthat().sourceCompatibility("26")
    }
}

extensions.configure<CheckstyleExtension> {
    toolVersion = taskmigoCatalog.findVersion("checkstyle").get().requiredVersion
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableAllChecks.set(true)
        error("NullAway", "RequireExplicitNullMarking")
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
        option("NullAway:HandleTestAssertionLibraries", "true")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
    }
}
