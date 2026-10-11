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
            "io.taskmigo.authorization.request.application.port.in.api",
            "io.taskmigo.authorization.object"
        ),
        List.of(),
        List.of("io.taskmigo.authorization.role.persistence..", "io.taskmigo.authorization.statement.persistence.."),
        List.of(
            "io.taskmigo.authorization.core",
            "io.taskmigo.authorization.object",
            "io.taskmigo.authorization.object.application.port.in.api",
            "io.taskmigo.authorization.object.application.port.out",
            "io.taskmigo.authorization.object.model",
            "io.taskmigo.authorization.provisioning",
            "io.taskmigo.authorization.provisioning.application.port.in.api",
            "io.taskmigo.authorization.request",
            "io.taskmigo.authorization.role",
            "io.taskmigo.authorization.role.application.api",
            "io.taskmigo.authorization.statement",
            "io.taskmigo.authorization.statement.application.api",
            "io.taskmigo.authorization.subject",
            "io.taskmigo.authorization.subject.application.port.in.api",
            "io.taskmigo.authorization.subject.application.port.out.resolution"
        )
    );

    @Test
    @DisplayName("enforces transitional Access Control Hexagonal and Onion production boundaries")
    void shouldEnforceProductionBoundariesWhenAuthorizationPackagesAreInspected() {
        HexagonalOnionRules.checkProduction(ARCHITECTURE);
    }

    @Test
    @DisplayName("keeps transitional Access Control tactical tests lightweight")
    void shouldKeepTacticalTestsLightweightWhenAuthorizationTestsAreInspected() {
        HexagonalOnionRules.checkTacticalTests(ARCHITECTURE);
    }
}
