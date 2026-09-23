package io.taskmigo.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Reusable ArchUnit rules for bounded contexts using Taskmigo's Hexagonal and Onion package model.
public final class HexagonalOnionRules {

    private HexagonalOnionRules() {}

    /// Describes the package boundaries for one bounded context.
    ///
    /// @param rootPackage root package of the bounded context
    /// @param applicationPackages current packages that contain application orchestration
    /// @param additionalDomainForbiddenPackages non-adapter packages that must remain outside the domain ring
    /// @param persistenceAdapterPackages current packages allowed to contain JPA or Spring Data dependencies
    /// @param publishedContractPackages current published contract packages protected from implementation leakage
    public record Context(
        String rootPackage,
        List<String> applicationPackages,
        List<String> additionalDomainForbiddenPackages,
        List<String> persistenceAdapterPackages,
        List<String> publishedContractPackages
    ) {
        public Context {
            rootPackage = Objects.requireNonNull(rootPackage);
            applicationPackages = List.copyOf(applicationPackages);
            additionalDomainForbiddenPackages = List.copyOf(additionalDomainForbiddenPackages);
            persistenceAdapterPackages = List.copyOf(persistenceAdapterPackages);
            publishedContractPackages = List.copyOf(publishedContractPackages);
        }
    }

    /// Checks production classes against both current tactical guards and the target Hexagonal/Onion package rules.
    ///
    /// @param context bounded-context package configuration
    public static void checkProduction(Context context) {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(context.rootPackage());

        for (ArchRule rule : productionRules(context)) {
            rule.check(classes);
        }
    }

    /// Checks domain/application unit-test packages remain independent from framework and persistence runtimes.
    ///
    /// @param context bounded-context package configuration
    public static void checkTacticalTests(Context context) {
        JavaClasses classes = new ClassFileImporter().importPackages(context.rootPackage());

        for (ArchRule rule : tacticalTestRules(context)) {
            rule.check(classes);
        }
    }

    /// Checks a target executable-application driving-adapter package depends on inbound contracts rather than
    /// application implementations, outbound ports, or driven adapters.
    ///
    /// The rule is empty-safe so it can be installed before a later phase moves existing adapters into the target
    /// `adapter.in` package.
    ///
    /// @param applicationRootPackage target application package root, such as `io.taskmigo.web`
    /// @param importRootPackage package root imported from the executable application's test classpath
    public static void checkDrivingApplication(String applicationRootPackage, String importRootPackage) {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(importRootPackage);

        drivingApplicationRule(applicationRootPackage).check(classes);
    }

    static List<ArchRule> productionRules(Context context) {
        ArrayList<ArchRule> rules = new ArrayList<>();
        rules.add(domainRule(context));
        rules.add(applicationRule(context));
        rules.add(plainApplicationServiceRule(context));
        rules.add(inboundPortRule(context));
        rules.add(outboundPortRule(context));
        rules.add(drivingAdapterRule(context));
        rules.add(drivenAdapterRule(context));
        rules.add(persistenceFrameworkContainmentRule(context));
        if (!context.publishedContractPackages().isEmpty()) {
            rules.add(publishedContractRule(context));
        }
        return List.copyOf(rules);
    }

    static List<ArchRule> tacticalTestRules(Context context) {
        ArchRule domainTestRule = noClasses()
            .that()
            .resideInAnyPackage(context.rootPackage() + "..domain..")
            .and()
            .haveSimpleNameEndingWith("Test")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .as("domain tests remain framework neutral");

        ArchRule applicationTestRule = noClasses()
            .that()
            .resideInAnyPackage(context.applicationPackages().toArray(String[]::new))
            .and()
            .haveSimpleNameEndingWith("Test")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                packageArray(
                    List.of(),
                    context.rootPackage() + "..adapter..",
                    "org.springframework.data..",
                    "jakarta.persistence..",
                    "org.springframework.boot.test.context..",
                    "org.springframework.test.context.."
                )
            )
            .as("application tests remain independent from persistence adapters and Spring test contexts");

        return List.of(domainTestRule, applicationTestRule);
    }

    static ArchRule domainRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.rootPackage() + "..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                packageArray(
                    combined(
                        List.of(context.rootPackage() + "..application.."),
                        context.additionalDomainForbiddenPackages()
                    ),
                    context.rootPackage() + "..adapter..",
                    "io.taskmigo.rest..",
                    "io.taskmigo.internal..",
                    "io.taskmigo.migration..",
                    "io.taskmigo.worker..",
                    "org.springframework..",
                    "jakarta.persistence.."
                )
            )
            .as("domain packages depend only inward and remain framework neutral");
    }

    static ArchRule applicationRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.applicationPackages().toArray(String[]::new))
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                packageArray(
                    List.of(),
                    context.rootPackage() + "..adapter..",
                    "io.taskmigo.rest..",
                    "io.taskmigo.internal..",
                    "io.taskmigo.migration..",
                    "io.taskmigo.worker..",
                    "org.springframework.data..",
                    "jakarta.persistence.."
                )
            )
            .as("application orchestration does not depend on outward adapters or persistence frameworks");
    }

    static ArchRule plainApplicationServiceRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.rootPackage() + "..application.service..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..")
            .allowEmptyShould(true)
            .as("target application services remain plain Java");
    }

    static ArchRule inboundPortRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.rootPackage() + "..application.port.in..")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                packageArray(
                    List.of(),
                    context.rootPackage() + "..application.service..",
                    context.rootPackage() + "..adapter..",
                    "io.taskmigo.rest..",
                    "io.taskmigo.internal..",
                    "io.taskmigo.migration..",
                    "io.taskmigo.worker..",
                    "org.springframework..",
                    "jakarta.persistence.."
                )
            )
            .allowEmptyShould(true)
            .as("inbound ports stay independent from implementations and adapters");
    }

    static ArchRule outboundPortRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.rootPackage() + "..application.port.out..")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                packageArray(
                    List.of(),
                    context.rootPackage() + "..application.service..",
                    context.rootPackage() + "..adapter..",
                    "io.taskmigo.rest..",
                    "io.taskmigo.internal..",
                    "io.taskmigo.migration..",
                    "io.taskmigo.worker..",
                    "org.springframework..",
                    "jakarta.persistence.."
                )
            )
            .allowEmptyShould(true)
            .as("outbound ports stay independent from implementations and adapters");
    }

    static ArchRule drivingAdapterRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.rootPackage() + "..adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                context.rootPackage() + "..application.service..",
                context.rootPackage() + "..application.port.out..",
                context.rootPackage() + "..adapter.out..",
                "org.springframework.data..",
                "jakarta.persistence.."
            )
            .allowEmptyShould(true)
            .as("driving adapters depend on inbound ports rather than use-case implementations or driven adapters");
    }

    static ArchRule drivenAdapterRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.rootPackage() + "..adapter.out..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                context.rootPackage() + "..application.service..",
                context.rootPackage() + "..application.port.in..",
                context.rootPackage() + "..adapter.in..",
                "io.taskmigo.rest..",
                "io.taskmigo.internal..",
                "io.taskmigo.migration..",
                "io.taskmigo.worker.."
            )
            .allowEmptyShould(true)
            .as("driven adapters depend inward on outbound/domain contracts and never on driving adapters");
    }

    static ArchRule persistenceFrameworkContainmentRule(Context context) {
        return noClasses()
            .that()
            .resideOutsideOfPackages(
                packageArray(
                    context.persistenceAdapterPackages(),
                    context.rootPackage() + "..adapter.out.persistence.."
                )
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.data..", "jakarta.persistence..")
            .as("JPA and Spring Data stay inside driven persistence adapters");
    }

    static ArchRule publishedContractRule(Context context) {
        return noClasses()
            .that()
            .arePublic()
            .and()
            .resideInAnyPackage(context.publishedContractPackages().toArray(String[]::new))
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                packageArray(
                    List.of(context.rootPackage() + "..application.."),
                    context.rootPackage() + "..adapter..",
                    "io.taskmigo.rest..",
                    "io.taskmigo.internal..",
                    "io.taskmigo.migration..",
                    "io.taskmigo.worker..",
                    "org.springframework.data..",
                    "jakarta.persistence.."
                )
            )
            .as("published contracts remain independent from tactical implementation types");
    }

    static ArchRule drivingApplicationRule(String applicationRootPackage) {
        return noClasses()
            .that()
            .resideInAnyPackage(applicationRootPackage + "..adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo..application.service..",
                "io.taskmigo..application.port.out..",
                "io.taskmigo..adapter.out..",
                "org.springframework.data..",
                "jakarta.persistence.."
            )
            .allowEmptyShould(true)
            .as(
                "executable application driving adapters depend on inbound ports, not implementations or driven adapters"
            );
    }

    private static List<String> combined(List<String> first, List<String> second) {
        ArrayList<String> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private static String[] packageArray(List<String> packages, String... additionalPackages) {
        ArrayList<String> result = new ArrayList<>(packages);
        result.addAll(List.of(additionalPackages));
        return result.toArray(String[]::new);
    }
}
