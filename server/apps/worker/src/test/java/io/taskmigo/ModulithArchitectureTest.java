package io.taskmigo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    /**
     * Verifies that the worker executable respects the current Spring Modulith graph.
     *
     * Given: the worker application and its production package graph.
     * Expect: Spring Modulith finds no module dependency or published-interface violation.
     */
    @Test
    @DisplayName("verifies application module boundaries")
    void shouldVerifyModuleBoundariesWhenApplicationModulesAreInspected() {
        // Arrange
        ApplicationModules modules = ApplicationModules.of(TaskmigoWorkerApplication.class);

        // Act + Assert
        modules.verify();
    }
}
