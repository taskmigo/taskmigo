package io.taskmigo.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityMembershipOnionArchitectureTest {

    @Test
    @DisplayName("keeps Identity Membership free from mandatory Hexagonal package vocabulary")
    void shouldRejectHexagonalPackageVocabularyWhenMembershipPackagesAreInspected() {
        noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.identity.membership.application.port..",
                "io.taskmigo.identity.membership.application.service..",
                "io.taskmigo.identity.membership.adapter..",
                "io.taskmigo.identity.membership.composition.."
            )
            .as(
                "Identity Membership uses responsibility-oriented Onion packages rather than mandatory Hexagonal vocabulary"
            )
            .check(membershipProductionClasses());
    }

    @Test
    @DisplayName("keeps Membership application code independent from outward implementations")
    void shouldKeepMembershipApplicationIndependentWhenMembershipPackagesAreInspected() {
        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.membership.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.membership.persistence..",
                "io.taskmigo.identity.membership.configuration..",
                "org.springframework.data..",
                "jakarta.persistence.."
            )
            .check(membershipProductionClasses());
    }

    @Test
    @DisplayName("keeps published Membership use cases independent from private application contracts")
    void shouldKeepPublishedUseCasesIndependentWhenMembershipPackagesAreInspected() {
        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.membership.application.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.membership.application.internal..",
                "io.taskmigo.identity.membership.persistence..",
                "io.taskmigo.identity.membership.configuration.."
            )
            .check(membershipProductionClasses());
    }

    @Test
    @DisplayName("keeps Spring Data and JPA usage inside Membership persistence")
    void shouldKeepPersistenceFrameworksInsideMembershipPersistenceWhenMembershipPackagesAreInspected() {
        noClasses()
            .that()
            .resideOutsideOfPackage("io.taskmigo.identity.membership.persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.data..", "jakarta.persistence..")
            .check(membershipProductionClasses());
    }

    private static JavaClasses membershipProductionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.identity.membership");
    }
}
