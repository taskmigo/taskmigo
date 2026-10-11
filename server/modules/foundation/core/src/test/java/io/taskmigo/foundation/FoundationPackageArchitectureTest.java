package io.taskmigo.foundation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FoundationPackageArchitectureTest {

    /**
     * Verifies that Foundation stays a minimal framework-neutral dependency floor.
     *
     * Given: every production class in Foundation.
     * Expect: Foundation does not depend on bounded contexts, supporting capabilities, executable applications, or
     * application/persistence frameworks.
     */
    @Test
    @DisplayName("keeps Foundation framework neutral and capability independent")
    void shouldKeepFoundationIndependentWhenHigherLevelCapabilitiesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule foundationDoesNotDependOnHigherLevelCapabilities = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.foundation..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization..",
                "io.taskmigo.identity..",
                "io.taskmigo.query..",
                "io.taskmigo.language..",
                "io.taskmigo.database..",
                "io.taskmigo.web..",
                "io.taskmigo.migration..",
                "io.taskmigo.worker..",
                "org.springframework..",
                "jakarta.persistence..",
                "jakarta.servlet..",
                "com.fasterxml.jackson..",
                "tools.jackson.."
            );

        // Act + Assert
        foundationDoesNotDependOnHigherLevelCapabilities.check(classes);
    }

    /**
     * Verifies that adding a new Foundation primitive requires an explicit dependency-floor ownership decision.
     *
     * Given: all production types directly owned by Foundation Core.
     * Expect: only the approved cross-capability failure and pagination contracts live at the dependency floor.
     */
    @Test
    @DisplayName("keeps Foundation Core limited to approved dependency-floor contracts")
    void shouldKeepOnlyApprovedContractsWhenFoundationCoreTypesAreInspected() {
        // Arrange
        Set<String> approvedContracts = Set.of(
            DomainException.class.getName(),
            DomainFailureType.class.getName(),
            OffsetPage.class.getName()
        );
        Set<String> actualContracts = productionClasses()
            .stream()
            .filter(type -> type.getPackageName().equals("io.taskmigo.foundation"))
            .map(type -> type.getName())
            .collect(Collectors.toSet());

        // Act + Assert
        assertThat(actualContracts).containsExactlyInAnyOrderElementsOf(approvedContracts);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.foundation");
    }
}
