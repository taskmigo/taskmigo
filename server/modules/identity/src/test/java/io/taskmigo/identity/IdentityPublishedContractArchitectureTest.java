package io.taskmigo.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityPublishedContractArchitectureTest {

    /**
     * Verifies published Identity data and use-case contracts remain independent from transport and representation
     * framework details after the package migration.
     *
     * Given: root capability contracts and deliberately published application APIs.
     * Expect: contracts do not depend on Spring Core/Web, Jackson, Servlet, or Authorization Server implementation APIs.
     */
    @Test
    @DisplayName("keeps published Identity contracts framework neutral")
    void shouldKeepPublishedContractsFrameworkNeutralWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule publishedContractsRemainFrameworkNeutral = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.identity.user",
                "io.taskmigo.identity.user.application.api..",
                "io.taskmigo.identity.group",
                "io.taskmigo.identity.group.application.api..",
                "io.taskmigo.identity.membership",
                "io.taskmigo.identity.membership.application.api..",
                "io.taskmigo.identity.configuration",
                "io.taskmigo.identity.configuration.application.api..",
                "io.taskmigo.identity.provisioning",
                "io.taskmigo.identity.provisioning.application.api..",
                "io.taskmigo.identity.authorization.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.core..",
                "com.fasterxml.jackson..",
                "tools.jackson..",
                "org.springframework.web..",
                "jakarta.servlet..",
                "org.springframework.security.oauth2.server.authorization.."
            );

        // Act + Assert
        publishedContractsRemainFrameworkNeutral.check(classes);
    }

    /**
     * Verifies published Identity use-case contracts cannot leak private application implementations or abstractions.
     *
     * Given: deliberately published capability application APIs.
     * Expect: published use cases depend on public contracts rather than capability-private application types.
     */
    @Test
    @DisplayName("keeps published Identity use cases independent from private application types")
    void shouldKeepPublishedUseCasesIndependentWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule publishedUseCasesRemainIndependent = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.identity.user.application.api..",
                "io.taskmigo.identity.group.application.api..",
                "io.taskmigo.identity.membership.application.api..",
                "io.taskmigo.identity.configuration.application.api..",
                "io.taskmigo.identity.provisioning.application.api.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.application",
                "io.taskmigo.identity.user.application",
                "io.taskmigo.identity.group.application",
                "io.taskmigo.identity.membership.application",
                "io.taskmigo.identity.configuration.application",
                "io.taskmigo.identity.provisioning.application",
                "io.taskmigo.identity..application.internal.."
            );

        // Act + Assert
        publishedUseCasesRemainIndependent.check(classes);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.identity");
    }
}
