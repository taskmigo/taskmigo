import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

val taskmigoCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("testImplementation", taskmigoCatalog.findLibrary("archunit-junit5").get())
}
