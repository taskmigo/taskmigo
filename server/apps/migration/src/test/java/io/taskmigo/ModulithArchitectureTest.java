package io.taskmigo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    /// Verifies that the migration application observes the same canonical module graph as runtime applications.
    ///
    /// Given: the migration application and all modules it composes.
    /// Expect: Spring Modulith rejects no dependency or named-interface boundary.
    @Test
    @DisplayName("verifies migration application module boundaries")
    void shouldVerifyMigrationModuleBoundariesWhenApplicationModulesAreInspected() {
        // Arrange
        ApplicationModules modules = ApplicationModules.of(TaskmigoMigrationApplication.class);

        // Act + Assert
        modules.verify();
    }
}
