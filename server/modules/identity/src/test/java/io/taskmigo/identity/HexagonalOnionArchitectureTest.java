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
        List.of(),
        List.of(),
        List.of(
            "io.taskmigo.identity.user",
            "io.taskmigo.identity.user.application.port.in.api",
            "io.taskmigo.identity.group",
            "io.taskmigo.identity.group.application.port.in.api",
            "io.taskmigo.identity.membership",
            "io.taskmigo.identity.membership.application.port.in.api",
            "io.taskmigo.identity.provisioning",
            "io.taskmigo.identity.provisioning.application.port.in.api",
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
