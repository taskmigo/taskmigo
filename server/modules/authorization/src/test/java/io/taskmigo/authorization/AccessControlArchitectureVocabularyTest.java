package io.taskmigo.authorization;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessControlArchitectureVocabularyTest {

    /// Verifies completed Onion migration cannot regress to mandatory Hexagonal package taxonomy.
    ///
    /// Given: all production classes owned by Access Control.
    /// Expect: no class resides in application port or directional adapter packages.
    @Test
    @DisplayName("rejects obsolete Hexagonal package conventions")
    void shouldRejectHexagonalPackageConventionsWhenAuthorizationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.taskmigo.authorization");
        ArchRule noHexagonalPackageConventions = noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.authorization..application.port.in..",
                "io.taskmigo.authorization..application.port.out..",
                "io.taskmigo.authorization..adapter.in..",
                "io.taskmigo.authorization..adapter.out.."
            );

        // Act + Assert
        noHexagonalPackageConventions.check(classes);
    }
}
