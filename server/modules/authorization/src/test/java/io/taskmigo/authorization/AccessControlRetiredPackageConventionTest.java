package io.taskmigo.authorization;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessControlRetiredPackageConventionTest {

    /// Verifies that Access Control cannot reintroduce mandatory Hexagonal package direction vocabulary.
    ///
    /// Given: all production classes owned by Access Control.
    /// Expect: no class resides in retired `application.port.in/out` or `adapter.in/out` packages.
    @Test
    @DisplayName("rejects retired Hexagonal package conventions")
    void shouldRejectRetiredHexagonalPackagesWhenAuthorizationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule noRetiredHexagonalPackages = noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.authorization..application.port.in..",
                "io.taskmigo.authorization..application.port.out..",
                "io.taskmigo.authorization..adapter.in..",
                "io.taskmigo.authorization..adapter.out.."
            );

        // Act + Assert
        noRetiredHexagonalPackages.check(classes);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.authorization");
    }
}
