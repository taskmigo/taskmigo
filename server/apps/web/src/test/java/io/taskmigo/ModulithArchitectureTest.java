package io.taskmigo;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    @Test
    @DisplayName("verifies application module boundaries")
    void shouldVerifyModuleBoundariesWhenApplicationModulesAreInspected() {
        ApplicationModules.of(TaskmigoApplication.class).verify();
    }

    /**
     * Verifies that Spring Modulith remains boundary metadata rather than a dependency of business or infrastructure
     * classes inside reusable server modules.
     *
     * Given: all production classes from Taskmigo's reusable server modules.
     * Expect: only package descriptors may depend on Spring Modulith annotations; ordinary classes stay independent.
     */
    @Test
    @DisplayName("keeps Spring Modulith metadata at package boundaries")
    void shouldKeepSpringModulithMetadataAtPackageBoundariesWhenServerModulesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                "io.taskmigo.authorization",
                "io.taskmigo.database",
                "io.taskmigo.foundation",
                "io.taskmigo.identity",
                "io.taskmigo.language",
                "io.taskmigo.query"
            );
        ArchRule ordinaryClassesDoNotDependOnSpringModulith = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.authorization..",
                "io.taskmigo.database..",
                "io.taskmigo.foundation..",
                "io.taskmigo.identity..",
                "io.taskmigo.language..",
                "io.taskmigo.query.."
            )
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.modulith..");

        // Act + Assert
        ordinaryClassesDoNotDependOnSpringModulith.check(classes);
    }

    /**
     * Verifies Enhancement #237 does not retain the superseded logical schema contracts after operation-scoped JPA
     * schemas become authoritative.
     *
     * Given: the production query module on the application classpath.
     * Expect: the retired persistence-neutral QuerySchema and QueryField types are absent.
     */
    @Test
    @DisplayName("rejects superseded logical query schema contracts")
    void shouldRejectSupersededLogicalQuerySchemaContracts() {
        assertThatThrownBy(() -> Class.forName("io.taskmigo.query.QuerySchema")).isInstanceOf(
            ClassNotFoundException.class
        );
        assertThatThrownBy(() -> Class.forName("io.taskmigo.query.QueryField")).isInstanceOf(
            ClassNotFoundException.class
        );
    }
}
