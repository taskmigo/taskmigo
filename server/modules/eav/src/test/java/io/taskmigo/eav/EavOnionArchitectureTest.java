package io.taskmigo.eav;

import io.taskmigo.architecture.OnionArchitectureRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EavOnionArchitectureTest {

    private static final OnionArchitectureRules.Context EAV = new OnionArchitectureRules.Context(
        "io.taskmigo.eav",
        List.of("io.taskmigo.eav..application.."),
        List.of("io.taskmigo.eav..persistence.."),
        List.of("io.taskmigo.eav..configuration.."),
        List.of(),
        List.of()
    );

    @Test
    @DisplayName("keeps EAV production dependencies inside Onion boundaries")
    void shouldKeepEavProductionDependenciesInward() {
        OnionArchitectureRules.checkProduction(EAV);
    }

    @Test
    @DisplayName("keeps EAV tactical tests independent from outward implementations")
    void shouldKeepEavTacticalTestsIndependentFromOutwardImplementations() {
        OnionArchitectureRules.checkTacticalTests(EAV);
    }
}
