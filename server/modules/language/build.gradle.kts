plugins {
    `java-library`
    antlr
}

tasks.generateGrammarSource {
    arguments.addAll(listOf("-visitor"))
}

spotless {
    java {
        targetExclude("build/generated-src/**")
    }
}

tasks.withType<org.gradle.api.plugins.quality.Checkstyle>().configureEach {
    exclude("**/build/generated-src/**")
    exclude("**/EmbeddedLanguage*.java")
}

description = "Taskmigo Language"

dependencies {
    api(libs.jspecify)
    implementation(libs.antlr.runtime)
    antlr(libs.antlr.tool)
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}
