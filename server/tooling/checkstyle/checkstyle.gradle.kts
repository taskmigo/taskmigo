import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.SourceSetContainer

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
    val isCheckstyleToolingProject = path == checkstyleToolingProjectPath

    pluginManager.withPlugin("java") {
        pluginManager.apply("checkstyle")

        dependencies {
            // Keep both the Checkstyle engine and Taskmigo rules on the verification-only
            // Checkstyle classpath. Neither dependency participates in application runtime.
            add("checkstyle", "com.puppycrawl.tools:checkstyle:$checkstyleVersion")
            if (!isCheckstyleToolingProject) {
                add("checkstyle", checkstyleToolingProject)
            }
        }

        extensions.configure<CheckstyleExtension> {
            toolVersion = checkstyleVersion
            configDirectory.set(checkstyleConfigDirectory)
            configFile = checkstyleConfigDirectory.file("checkstyle.xml").asFile
        }

        val sourceSets = extensions.getByType<SourceSetContainer>()

        tasks.withType<Checkstyle>().configureEach {
            if (isCheckstyleToolingProject) {
                // Self-host without a project dependency cycle. Checkstyle tasks depend on
                // source-set classes, so the custom rule and its messages are available here.
                checkstyleClasspath =
                    configurations.getByName("checkstyle") + sourceSets.named("main").get().output
            }

            reports {
                xml.required.set(false)
                html.required.set(true)
            }
        }
    }
}
