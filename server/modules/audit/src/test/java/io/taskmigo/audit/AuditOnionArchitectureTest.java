package io.taskmigo.audit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.taskmigo.architecture.OnionArchitectureRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditOnionArchitectureTest {

    private static final OnionArchitectureRules.Context AUDIT = new OnionArchitectureRules.Context(
        "io.taskmigo.audit",
        List.of("io.taskmigo.audit..application.."),
        List.of("io.taskmigo.audit..persistence.."),
        List.of("io.taskmigo.audit..configuration.."),
        List.of(),
        List.of()
    );

    /**
     * Verifies that Audit production dependencies continue to point inward after transaction simplification.
     *
     * Given: all production classes in the Audit module.
     * Expect: application, persistence, and configuration dependencies satisfy the shared Onion rules.
     */
    @Test
    @DisplayName("keeps Audit production dependencies inward")
    void shouldKeepAuditProductionDependenciesInwardWhenCheckingModuleArchitecture() {
        // Act + Assert
        OnionArchitectureRules.checkProduction(AUDIT);
    }

    /**
     * Verifies that tactical application tests do not depend on outward Audit implementations.
     *
     * Given: all test classes in the Audit module.
     * Expect: tactical tests satisfy the shared Onion test-isolation rules.
     */
    @Test
    @DisplayName("keeps Audit tactical tests isolated from outward implementations")
    void shouldKeepAuditTacticalTestsIsolatedWhenCheckingModuleArchitecture() {
        // Act + Assert
        OnionArchitectureRules.checkTacticalTests(AUDIT);
    }

    /**
     * Verifies that removed Hexagonal package vocabulary cannot return unnoticed.
     *
     * Given: all production classes in the Audit module.
     * Expect: no class resides in the retired port, service, adapter, composition, or infrastructure package trees.
     */
    @Test
    @DisplayName("rejects retired Audit Hexagonal package vocabulary")
    void shouldRejectRetiredAuditPackageVocabularyWhenCheckingProductionPackages() {
        // Arrange
        JavaClasses classes = productionClasses();

        // Act + Assert
        noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.audit..application.port..",
                "io.taskmigo.audit..application.service..",
                "io.taskmigo.audit..adapter..",
                "io.taskmigo.audit..composition..",
                "io.taskmigo.audit..infrastructure.."
            )
            .allowEmptyShould(true)
            .check(classes);
    }

    /**
     * Verifies that the removed Audit transaction-runner architecture cannot return unnoticed.
     *
     * Given: all production classes in Audit after declarative transaction migration.
     * Expect: neither the legacy runner contract nor its transaction implementation package remains.
     */
    @Test
    @DisplayName("rejects the legacy Audit transaction runner")
    void shouldRejectLegacyTransactionRunnerWhenAuditPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();

        // Act + Assert
        noClasses()
            .should()
            .haveFullyQualifiedName("io.taskmigo.audit.application.AuditTransactionRunner")
            .check(classes);
        noClasses().should().resideInAnyPackage("io.taskmigo.audit.transaction..").check(classes);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.audit");
    }
}
