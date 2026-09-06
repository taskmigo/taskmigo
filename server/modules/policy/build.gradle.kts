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
    exclude("**/PolicyLanguage*.java")
}

description = "Taskmigo Policy Language"

dependencies {
    api(libs.jspecify)
    implementation(libs.antlr.runtime)
    antlr(libs.antlr.tool)
    testImplementation(libs.spring.boot.starter.test)
}
