package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.taskmigo.architecture.HexagonalOnionRules;
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

    /**
     * Verifies that target driving adapters cannot bypass inbound ports.
     *
     * Given: the worker package graph, currently without production background-job adapters.
     * Expect: the shared Hexagonal/Onion rule accepts the empty driving-adapter surface and rejects any future
     * `adapter.in` dependency on application-service implementations, outbound ports, driven adapters, Spring Data,
     * or JPA.
     */
    @Test
    @DisplayName("keeps target worker driving adapters on inbound ports")
    void shouldKeepDrivingAdaptersOnInboundPortsWhenWorkerPackagesAreInspected() {
        // Arrange
        String applicationRootPackage = "io.taskmigo.worker";
        String importRootPackage = "io.taskmigo.worker";

        // Act + Assert
        assertThatCode(() ->
            HexagonalOnionRules.checkDrivingApplication(applicationRootPackage, importRootPackage)
        ).doesNotThrowAnyException();
    }
}
