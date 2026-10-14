package io.taskmigo.web;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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
        JavaClasses classes = productionClasses();
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

    @Test
    @DisplayName("keeps web production classes in responsibility packages")
    void shouldKeepProductionClassesInResponsibilityPackages() {
        // Arrange
        JavaClasses imported = productionClasses();
        ArchRule responsibilityPackages = classes()
            .that()
            .resideInAPackage("io.taskmigo.web..")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .resideInAnyPackage(
                "io.taskmigo.web.api..",
                "io.taskmigo.web.security..",
                "io.taskmigo.web.oauth..",
                "io.taskmigo.web.configuration.."
            );

        // Act + Assert
        responsibilityPackages.check(imported);
    }

    @Test
    @DisplayName("keeps shared web security independent of API v0")
    void shouldKeepSecurityIndependentOfApiV0() {
        // Arrange
        JavaClasses imported = productionClasses();
        ArchRule securityDoesNotDependOnApiV0 = noClasses()
            .that()
            .resideInAPackage("io.taskmigo.web.security..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.taskmigo.web.api.v0..");

        // Act + Assert
        securityDoesNotDependOnApiV0.check(imported);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.web");
    }
}
