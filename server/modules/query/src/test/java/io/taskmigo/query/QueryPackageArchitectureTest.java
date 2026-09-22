package io.taskmigo.query;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryPackageArchitectureTest {

    /**
     * Verifies that Query contracts stay independent from framework-specific type, transport, persistence, and OAuth
     * implementations while still allowing Spring stereotypes and Spring Modulith boundary annotations.
     *
     * Given: all production classes in the Query module.
     * Expect: no Query class depends on Spring Core type descriptors, Jackson, Spring Web, Servlet, Spring Data, JPA,
     * or Spring Authorization Server implementation packages.
     */
    @Test
    @DisplayName("keeps Query contracts framework neutral")
    void shouldKeepQueryContractsFrameworkNeutralWhenQueryPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule queryContractsDoNotDependOnFrameworkDetails = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.query..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.core..",
                "com.fasterxml.jackson..",
                "tools.jackson..",
                "org.springframework.web..",
                "jakarta.servlet..",
                "org.springframework.data..",
                "jakarta.persistence..",
                "org.springframework.security.oauth2.server.authorization.."
            );

        // Act + Assert
        queryContractsDoNotDependOnFrameworkDetails.check(classes);
    }

    /**
     * Verifies that the persistence-neutral Query model is not placed under a persistence adapter package.
     *
     * Given: all production classes in the Query module.
     * Expect: no class resides in the retired `io.taskmigo.query.persistence` package.
     */
    @Test
    @DisplayName("rejects the retired Query persistence package")
    void shouldRejectPersistencePackageWhenQueryPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule noLegacyPersistencePackage = noClasses()
            .should()
            .resideInAnyPackage("io.taskmigo.query.persistence..");

        // Act + Assert
        noLegacyPersistencePackage.check(classes);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.query");
    }
}
