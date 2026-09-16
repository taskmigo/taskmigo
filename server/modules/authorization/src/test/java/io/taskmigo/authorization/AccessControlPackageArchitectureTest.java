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
}
