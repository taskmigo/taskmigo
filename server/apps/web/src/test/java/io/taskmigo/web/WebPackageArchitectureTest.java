package io.taskmigo.web;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebPackageArchitectureTest {

    @Test
    @DisplayName("rejects retired web package names")
    void shouldRejectRetiredWebPackagesWhenProductionPackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.web");
        ArchRule noRetiredWebPackages = noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.web.adapter..",
                "io.taskmigo.web.composition..",
                "io.taskmigo.rest..",
                "io.taskmigo.internal.."
            );

        // Act + Assert
        noRetiredWebPackages.check(classes);
    }
}
