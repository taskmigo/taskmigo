package io.taskmigo;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import io.taskmigo.architecture.HexagonalOnionRules;
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
     * Verifies that target driving adapters cannot bypass inbound ports.
     *
     * Given: HTTP and security driving adapters under `io.taskmigo.web.adapter.in`.
     * Expect: the shared Hexagonal/Onion rule rejects dependencies on application-service implementations, outbound
     * ports, driven adapters, Spring Data, or JPA.
     */
    @Test
    @DisplayName("keeps target web driving adapters on inbound ports")
    void shouldKeepDrivingAdaptersOnInboundPortsWhenWebPackagesAreInspected() {
        // Arrange
        String applicationRootPackage = "io.taskmigo.web";
        String importRootPackage = "io.taskmigo";

        // Act + Assert
        assertThatCode(() ->
            HexagonalOnionRules.checkDrivingApplication(applicationRootPackage, importRootPackage)
        ).doesNotThrowAnyException();
    }

    /**
     * Verifies Phase 4 adapter migrations cannot regress to legacy web package names.
     *
     * Given: production classes on the web application classpath.
     * Expect: no class remains under the retired REST or internal-security package roots.
     */
    @Test
    @DisplayName("rejects retired web adapter package names")
    void shouldRejectLegacyWebAdapterPackagesWhenWebPackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo");
        ArchRule noLegacyWebAdapterPackages = noClasses()
            .should()
            .resideInAnyPackage("io.taskmigo.rest..", "io.taskmigo.internal.security..");

        // Act + Assert
        noLegacyWebAdapterPackages.check(classes);
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
}
