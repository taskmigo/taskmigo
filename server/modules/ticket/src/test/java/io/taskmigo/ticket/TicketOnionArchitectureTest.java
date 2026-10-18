package io.taskmigo.ticket;

import io.taskmigo.architecture.OnionArchitectureRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TicketOnionArchitectureTest {

    private static final OnionArchitectureRules.Context TICKET = new OnionArchitectureRules.Context(
        "io.taskmigo.ticket",
        List.of("io.taskmigo.ticket..application.."),
        List.of("io.taskmigo.ticket..persistence.."),
        List.of("io.taskmigo.ticket..configuration.."),
        List.of(),
        List.of()
    );

    @Test
    @DisplayName("keeps Ticket production dependencies inward")
    void shouldKeepTicketProductionDependenciesInward() {
        OnionArchitectureRules.checkProduction(TICKET);
    }

    @Test
    @DisplayName("keeps Ticket tactical tests isolated from outward implementations")
    void shouldKeepTicketTacticalTestsIsolated() {
        OnionArchitectureRules.checkTacticalTests(TICKET);
    }
}
