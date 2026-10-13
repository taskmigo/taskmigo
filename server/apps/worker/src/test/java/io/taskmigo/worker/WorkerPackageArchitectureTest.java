package io.taskmigo.worker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkerPackageArchitectureTest {

    /**
     * Verifies that Worker production code no longer uses retired architecture-pattern package names.
     *
     * Given: all production classes below io.taskmigo.worker.
     * Expect: no class resides below adapter, application, or composition package roots.
     */
    @Test
    @DisplayName("rejects retired Worker package names")
    void shouldRejectRetiredWorkerPackagesWhenProductionPackagesAreInspected() {
        // Arrange
        JavaClasses imported = productionClasses();
        ArchRule noRetiredWorkerPackages = noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.worker.adapter..",
                "io.taskmigo.worker.application..",
                "io.taskmigo.worker.composition.."
            );

        // Act + Assert
        noRetiredWorkerPackages.check(imported);
    }

    /**
     * Verifies that Worker production classes live in explicit runtime-responsibility packages.
     *
     * Given: all ordinary production classes below io.taskmigo.worker.
     * Expect: every class belongs to the retention runtime responsibility.
     */
    @Test
    @DisplayName("keeps Worker production classes in responsibility packages")
    void shouldKeepProductionClassesInResponsibilityPackagesWhenWorkerPackagesAreInspected() {
        // Arrange
        JavaClasses imported = productionClasses();
        ArchRule responsibilityPackages = classes()
            .that()
            .resideInAPackage("io.taskmigo.worker..")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .resideInAPackage("io.taskmigo.worker.retention..");

        // Act + Assert
        responsibilityPackages.check(imported);
    }

    /**
     * Verifies that Worker runtime responsibilities do not become persistence owners.
     *
     * Given: all production classes below io.taskmigo.worker.
     * Expect: no Worker class depends directly on Spring Data or Jakarta Persistence APIs.
     */
    @Test
    @DisplayName("keeps Worker runtime responsibilities independent from persistence frameworks")
    void shouldKeepRuntimeResponsibilitiesIndependentFromPersistenceFrameworksWhenWorkerPackagesAreInspected() {
        // Arrange
        JavaClasses imported = productionClasses();
        ArchRule noPersistenceFrameworkDependencies = noClasses()
            .that()
            .resideInAPackage("io.taskmigo.worker..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.data..", "jakarta.persistence..");

        // Act + Assert
        noPersistenceFrameworkDependencies.check(imported);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.worker");
    }
}
