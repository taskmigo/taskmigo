package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.modulith.docs.Documenter.CanvasOptions;
import org.springframework.modulith.docs.Documenter.DiagramOptions;
import org.springframework.modulith.docs.Documenter.DiagramOptions.ElementsWithoutRelationships;

class ModulithDocumentationTest {

    private static final Path DOCUMENTATION_DIRECTORY = Path.of("build", "spring-modulith-docs");

    /// Verifies that the web application's complete Spring Modulith graph can be materialized as generated
    /// architecture documentation.
    ///
    /// Given: the application modules discovered from [TaskmigoApplication], including modules without dependencies.
    /// Expect: Spring Modulith writes the aggregate component diagram and aggregating AsciiDoc document.
    @Test
    @DisplayName("writes application module architecture documentation")
    void shouldWriteArchitectureDocumentationWhenApplicationModulesAreDocumented() {
        // Arrange
        ApplicationModules modules = ApplicationModules.of(TaskmigoApplication.class);
        DiagramOptions diagramOptions = DiagramOptions.defaults()
            .withElementsWithoutRelationships(ElementsWithoutRelationships.VISIBLE);

        // Act
        new Documenter(modules).writeDocumentation(diagramOptions, CanvasOptions.defaults());

        // Assert
        assertThat(DOCUMENTATION_DIRECTORY.resolve("components.puml")).isRegularFile();
        assertThat(DOCUMENTATION_DIRECTORY.resolve("all-docs.adoc")).isRegularFile();
    }
}
