package io.taskmigo.authorization;

import io.taskmigo.architecture.HexagonalOnionRules;
import io.taskmigo.architecture.HexagonalOnionRules.Context;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HexagonalOnionArchitectureTest {

    private static final Context ARCHITECTURE = new Context(
        "io.taskmigo.authorization",
        List.of(
            "io.taskmigo.authorization..application..",
            "io.taskmigo.authorization.request",
            "io.taskmigo.authorization.object"
        ),
        List.of("io.taskmigo.authorization..infrastructure..", "io.taskmigo.authorization.persistence.."),
        List.of("io.taskmigo.authorization.object.persistence.."),
        List.of("io.taskmigo.authorization..infrastructure.persistence..", "io.taskmigo.authorization.persistence.."),
        List.of(
            "io.taskmigo.authorization.core",
            "io.taskmigo.authorization.object",
            "io.taskmigo.authorization.provisioning",
            "io.taskmigo.authorization.request",
            "io.taskmigo.authorization.role",
            "io.taskmigo.authorization.role.application.port.in.api",
            "io.taskmigo.authorization.spi",
            "io.taskmigo.authorization.statement",
            "io.taskmigo.authorization.statement.application.port.in.api",
            "io.taskmigo.authorization.subject",
            "io.taskmigo.authorization.subject.application.port.in.api",
            "io.taskmigo.authorization.subject.application.port.out.resolution"
        )
    );

    @Test
    @DisplayName("enforces Access Control Hexagonal and Onion production boundaries")
    void shouldEnforceProductionBoundariesWhenAuthorizationPackagesAreInspected() {
        HexagonalOnionRules.checkProduction(ARCHITECTURE);
    }

    @Test
    @DisplayName("keeps Access Control tactical tests lightweight")
    void shouldKeepTacticalTestsLightweightWhenAuthorizationTestsAreInspected() {
        HexagonalOnionRules.checkTacticalTests(ARCHITECTURE);
    }
}
