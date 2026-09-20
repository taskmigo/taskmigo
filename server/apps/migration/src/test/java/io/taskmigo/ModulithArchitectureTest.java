package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.taskmigo.architecture.HexagonalOnionRules;
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

    /// Verifies that target driving adapters cannot bypass inbound ports.
    ///
    /// Given: the migration application package graph before Phase 4 moves migration/provisioning entry points under
    /// `adapter.in`.
    /// Expect: the shared Hexagonal/Onion driving-adapter rule accepts the current package graph and will reject
    /// dependencies on application-service implementations, outbound ports, driven adapters, Spring Data, or JPA.
    @Test
    @DisplayName("keeps target migration driving adapters on inbound ports")
    void shouldKeepDrivingAdaptersOnInboundPortsWhenMigrationPackagesAreInspected() {
        // Arrange
        String applicationRootPackage = "io.taskmigo.migration";
        String importRootPackage = "io.taskmigo.migration";

        // Act + Assert
        assertThatCode(() -> HexagonalOnionRules.checkDrivingApplication(applicationRootPackage, importRootPackage))
            .doesNotThrowAnyException();
    }
}
