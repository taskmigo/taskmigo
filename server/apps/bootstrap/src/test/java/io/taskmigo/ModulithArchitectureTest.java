package io.taskmigo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    /**
     * Verifies that the bootstrap application observes the same canonical module graph as runtime applications.
     *
     * Given: the bootstrap application and all modules it composes.
     * Expect: Spring Modulith rejects no dependency or named-interface boundary.
     */
    @Test
    @DisplayName("verifies bootstrap application module boundaries")
    void shouldVerifyBootstrapModuleBoundariesWhenApplicationModulesAreInspected() {
        // Arrange
        ApplicationModules modules = ApplicationModules.of(TaskmigoBootstrapApplication.class);

        // Act + Assert
        modules.verify();
    }
}
