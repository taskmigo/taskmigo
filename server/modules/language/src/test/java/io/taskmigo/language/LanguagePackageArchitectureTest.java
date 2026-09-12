package io.taskmigo.language;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LanguagePackageArchitectureTest {

    /**
     * Verifies that direct evaluation remains independent from the generated parser frontend.
     *
     * Given: the compiled classes of the Language module.
     * Expect: EmbeddedLanguageEvaluator has no direct dependency on ANTLR runtime or generated parser classes.
     */
    @Test
    @DisplayName("keeps direct evaluation independent from the parser frontend")
    void shouldKeepDirectEvaluationIndependentWhenLanguagePackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter().importPackages("io.taskmigo.language");
        ArchRule evaluatorDoesNotDependOnFrontend = noClasses()
            .that()
            .haveSimpleName("EmbeddedLanguageEvaluator")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.antlr..", "io.taskmigo.language.antlr..");

        // Act + Assert
        evaluatorDoesNotDependOnFrontend.check(classes);
    }

    /**
     * Verifies that partial evaluation remains independent from the generated parser frontend.
     *
     * Given: the compiled classes of the Language module.
     * Expect: EmbeddedLanguagePartialEvaluator has no direct dependency on ANTLR runtime or generated parser classes.
     */
    @Test
    @DisplayName("keeps partial evaluation independent from the parser frontend")
    void shouldKeepPartialEvaluationIndependentWhenLanguagePackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter().importPackages("io.taskmigo.language");
        ArchRule partialEvaluatorDoesNotDependOnFrontend = noClasses()
            .that()
            .haveSimpleName("EmbeddedLanguagePartialEvaluator")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.antlr..", "io.taskmigo.language.antlr..");

        // Act + Assert
        partialEvaluatorDoesNotDependOnFrontend.check(classes);
    }

    /**
     * Verifies that ANTLR stays confined to the parser/compiler frontend.
     *
     * Given: every compiled class outside the generated parser package.
     * Expect: only LanguageCompilerVisitor and the EmbeddedLanguageCompiler implementation, including its private
     * TokenSource and error-listener classes, may depend directly on ANTLR or generated parser types.
     */
    @Test
    @DisplayName("confines ANTLR dependencies to the compiler frontend")
    void shouldConfineAntlrDependenciesWhenLanguagePackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter().importPackages("io.taskmigo.language");
        ArchRule onlyCompilerFrontendDependsOnAntlr = noClasses()
            .that()
            .resideOutsideOfPackage("io.taskmigo.language.antlr..")
            .and()
            .doNotHaveSimpleName("EmbeddedLanguageCompiler")
            .and()
            .doNotHaveSimpleName("BoundedTokenSource")
            .and()
            .doNotHaveSimpleName("Errors")
            .and()
            .doNotHaveSimpleName("LanguageCompilerVisitor")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.antlr..", "io.taskmigo.language.antlr..");

        // Act + Assert
        onlyCompilerFrontendDependsOnAntlr.check(classes);
    }
}
