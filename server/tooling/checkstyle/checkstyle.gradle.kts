import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

val checkstyleToolingProjectPath = ":tooling:checkstyle"
val checkstyleToolingProject = project(checkstyleToolingProjectPath)
val checkstyleConfigDirectory = checkstyleToolingProject.layout.projectDirectory.dir("config")
val checkstyleVersion = extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("checkstyle")
    .orElseThrow()
    .requiredVersion

subprojects {
    if (path != checkstyleToolingProjectPath) {
        pluginManager.withPlugin("java") {
            pluginManager.apply("checkstyle")

            dependencies {
                // Keep both the Checkstyle engine and Taskmigo rules on the verification-only
                // Checkstyle classpath. Neither dependency participates in application runtime.
                add("checkstyle", "com.puppycrawl.tools:checkstyle:$checkstyleVersion")
                add("checkstyle", checkstyleToolingProject)
            }

            extensions.configure<CheckstyleExtension> {
                toolVersion = checkstyleVersion
                configDirectory.set(checkstyleConfigDirectory)
                configFile = checkstyleConfigDirectory.file("checkstyle.xml").asFile
            }

            tasks.withType<Checkstyle>().configureEach {
                reports {
                    xml.required.set(false)
                    html.required.set(true)
                }
            }
        }
    }
}
