package io.taskmigo.eav;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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

    /**
     * Verifies that the removed EAV transaction-runner architecture cannot return unnoticed.
     *
     * Given: all EAV production classes after declarative transaction migration.
     * Expect: neither the legacy application runner nor its Spring adapter remains.
     */
    @Test
    @DisplayName("rejects the legacy EAV transaction runner")
    void shouldRejectLegacyTransactionRunnerWhenEavPackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.eav");

        // Act + Assert
        noClasses()
            .should()
            .haveFullyQualifiedName("io.taskmigo.eav.schema.application.EavTransactionRunner")
            .check(classes);
        noClasses()
            .should()
            .haveFullyQualifiedName("io.taskmigo.eav.schema.configuration.SpringEavTransactionRunner")
            .check(classes);
    }
}
