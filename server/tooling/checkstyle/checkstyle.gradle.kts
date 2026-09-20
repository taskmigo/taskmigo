import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

val checkstyleToolingProjectPath = ":tooling:checkstyle"

subprojects {
    if (path != checkstyleToolingProjectPath) {
        pluginManager.withPlugin("java") {
            pluginManager.apply("checkstyle")

            dependencies {
                add("checkstyle", project(checkstyleToolingProjectPath))
            }

            extensions.configure<CheckstyleExtension> {
                configFile = project(checkstyleToolingProjectPath).file("config/checkstyle.xml")
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
