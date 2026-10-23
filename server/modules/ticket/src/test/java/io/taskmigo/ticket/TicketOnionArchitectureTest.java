package io.taskmigo.ticket;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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

    @Test
    @DisplayName("keeps EAV implementation types out of the Ticket public API")
    void shouldKeepEavImplementationTypesOutOfTicketPublicApi() {
        JavaClasses classes = productionClasses();

        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.ticket.application.api..", "io.taskmigo.ticket.type.application.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.eav..")
            .check(classes);
    }

    /**
     * Verifies that the removed Ticket transaction-runner architecture cannot return unnoticed.
     *
     * Given: all Ticket production classes after declarative transaction migration.
     * Expect: neither the legacy application runner nor its Spring adapter remains.
     */
    @Test
    @DisplayName("rejects the legacy Ticket transaction runner")
    void shouldRejectLegacyTransactionRunnerWhenTicketPackagesAreInspected() {
        JavaClasses classes = productionClasses();

        noClasses()
            .should()
            .haveFullyQualifiedName("io.taskmigo.ticket.application.TicketTransactionRunner")
            .check(classes);
        noClasses()
            .should()
            .haveFullyQualifiedName("io.taskmigo.ticket.configuration.SpringTicketTransactionRunner")
            .check(classes);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.ticket");
    }
}
