package io.taskmigo.authorization;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessControlPackageArchitectureTest {

    /**
     * Verifies the bounded-context dependency direction between Access Control and Identity.
     *
     * Given: all classes owned by Access Control.
     * Expect: Access Control remains independent from Identity resources and persistence.
     */
    @Test
    @DisplayName("keeps Access Control independent from Identity")
    void shouldKeepAccessControlIndependentWhenAuthorizationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter().importPackages("io.taskmigo.authorization");
        ArchRule accessControlDoesNotDependOnIdentity = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.authorization..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.identity..");

        // Act + Assert
        accessControlDoesNotDependOnIdentity.check(classes);
    }

    /**
     * Verifies that Access Control's public use-case and domain packages do not reach into JPA adapters.
     *
     * Given: Role, Statement, and subject-binding application contracts.
     * Expect: their dependencies exclude Access Control persistence and Spring Data packages.
     */
    @Test
    @DisplayName("keeps Access Control use cases independent from JPA adapters")
    void shouldKeepUseCasesIndependentWhenAccessControlPackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter().importPackages("io.taskmigo.authorization");
        ArchRule useCasesDoNotDependOnJpaAdapters = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.authorization.role..",
                "io.taskmigo.authorization.statement..",
                "io.taskmigo.authorization.subject..",
                "io.taskmigo.authorization.core.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.authorization.persistence..", "org.springframework.data..");

        // Act + Assert
        useCasesDoNotDependOnJpaAdapters.check(classes);
    }
}
