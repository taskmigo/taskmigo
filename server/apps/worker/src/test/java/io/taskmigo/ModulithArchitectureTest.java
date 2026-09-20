package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.taskmigo.architecture.HexagonalOnionRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    @Test
    @DisplayName("verifies application module boundaries")
    void shouldVerifyModuleBoundariesWhenApplicationModulesAreInspected() {
        ApplicationModules.of(TaskmigoWorkerApplication.class).verify();
    }

    /**
     * Verifies that target driving adapters cannot bypass inbound ports.
     *
     * Given: the worker application package graph before Phase 4 moves background-job entry points under `adapter.in`.
     * Expect: the shared Hexagonal/Onion driving-adapter rule accepts the current package graph and will reject
     * dependencies on application-service implementations, outbound ports, driven adapters, Spring Data, or JPA.
     */
    @Test
    @DisplayName("keeps target worker driving adapters on inbound ports")
    void shouldKeepDrivingAdaptersOnInboundPortsWhenWorkerPackagesAreInspected() {
        // Arrange
        String applicationRootPackage = "io.taskmigo.worker";
        String importRootPackage = "io.taskmigo.worker";

        // Act + Assert
        assertThatCode(() -> HexagonalOnionRules.checkDrivingApplication(applicationRootPackage, importRootPackage))
            .doesNotThrowAnyException();
    }
}
