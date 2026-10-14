package io.taskmigo.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityTacticalTestArchitectureTest {

    /**
     * Verifies Identity domain tests remain independent from Spring runtime and test infrastructure.
     *
     * Given: tests colocated with Identity domain packages.
     * Expect: domain tests do not depend on Spring APIs.
     */
    @Test
    @DisplayName("keeps Identity domain tests framework neutral")
    void shouldKeepDomainTestsFrameworkNeutralWhenIdentityTestsAreInspected() {
        // Arrange
        JavaClasses classes = identityClasses();
        ArchRule domainTestsRemainFrameworkNeutral = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity..domain..")
            .and()
            .haveSimpleNameEndingWith("Test")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..");

        // Act + Assert
        domainTestsRemainFrameworkNeutral.check(classes);
    }

    /**
     * Verifies Identity application tests stay independent from outward implementations and Spring test contexts.
     *
     * Given: tests colocated with Identity application packages.
     * Expect: application tests do not depend on persistence/integration implementations, JPA, Spring Data, or Spring
     * test contexts.
     */
    @Test
    @DisplayName("keeps Identity application tests independent from outward implementations")
    void shouldKeepApplicationTestsIndependentWhenIdentityTestsAreInspected() {
        // Arrange
        JavaClasses classes = identityClasses();
        ArchRule applicationTestsRemainIndependent = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity..application..")
            .and()
            .haveSimpleNameEndingWith("Test")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity..persistence..",
                "io.taskmigo.identity..integration..",
                "io.taskmigo.identity.transaction..",
                "org.springframework.data..",
                "jakarta.persistence..",
                "org.springframework.boot.test.context..",
                "org.springframework.test.context.."
            );

        // Act + Assert
        applicationTestsRemainIndependent.check(classes);
    }

    private static JavaClasses identityClasses() {
        return new ClassFileImporter().importPackages("io.taskmigo.identity");
    }
}
