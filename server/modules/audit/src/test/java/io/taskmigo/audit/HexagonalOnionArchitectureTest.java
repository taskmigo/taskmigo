package io.taskmigo.audit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HexagonalOnionArchitectureTest {

    /**
     * Verifies that Audit contracts and application services remain independent from driven adapters and framework
     * implementation details.
     *
     * Given: Audit production classes.
     * Expect: model and application packages do not depend on persistence, JobRunr, Spring application mechanics, or
     * JSON serialization.
     */
    @Test
    @DisplayName("keeps Audit contracts and application services framework neutral")
    void shouldKeepAuditContractsAndApplicationServicesFrameworkNeutral() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule contractsStayInward = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.audit.model..", "io.taskmigo.audit.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.audit.adapter..",
                "jakarta.persistence..",
                "org.jobrunr..",
                "org.springframework.context..",
                "org.springframework.data..",
                "org.springframework.stereotype..",
                "org.springframework.transaction..",
                "org.springframework.web..",
                "tools.jackson.."
            );

        // Act + Assert
        contractsStayInward.check(classes);
    }

    /**
     * Verifies that Audit remains independent from executable applications and business bounded contexts.
     *
     * Given: Audit production classes.
     * Expect: no Audit class depends on web, worker, migration, Identity, or Access Control implementation packages.
     */
    @Test
    @DisplayName("keeps Audit independent from consumers")
    void shouldKeepAuditIndependentFromConsumers() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule auditDoesNotDependOnConsumers = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.audit..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization..",
                "io.taskmigo.identity..",
                "io.taskmigo.migration..",
                "io.taskmigo.web..",
                "io.taskmigo.worker.."
            );

        // Act + Assert
        auditDoesNotDependOnConsumers.check(classes);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.audit");
    }
}
