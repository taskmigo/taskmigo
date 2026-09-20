package io.taskmigo.identity;

import io.taskmigo.architecture.HexagonalOnionRules;
import io.taskmigo.architecture.HexagonalOnionRules.Context;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HexagonalOnionArchitectureTest {

    private static final Context ARCHITECTURE = new Context(
        "io.taskmigo.identity",
        List.of("io.taskmigo.identity..application.."),
        List.of("io.taskmigo.identity..infrastructure..", "io.taskmigo.identity.persistence.."),
        List.of(),
        List.of("io.taskmigo.identity..infrastructure.persistence..", "io.taskmigo.identity.persistence.."),
        List.of(
            "io.taskmigo.identity.user",
            "io.taskmigo.identity.group",
            "io.taskmigo.identity.membership",
            "io.taskmigo.identity.provisioning",
            "io.taskmigo.identity.authorization"
        )
    );

    @Test
    @DisplayName("enforces Identity Hexagonal and Onion production boundaries")
    void shouldEnforceProductionBoundariesWhenIdentityPackagesAreInspected() {
        HexagonalOnionRules.checkProduction(ARCHITECTURE);
    }

    @Test
    @DisplayName("keeps Identity tactical tests lightweight")
    void shouldKeepTacticalTestsLightweightWhenIdentityTestsAreInspected() {
        HexagonalOnionRules.checkTacticalTests(ARCHITECTURE);
    }
}
