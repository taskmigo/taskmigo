package io.taskmigo.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Reusable ArchUnit rules for Taskmigo's responsibility-oriented DDD and Onion architecture.
public final class OnionArchitectureRules {

    private static final List<String> EXECUTABLE_APPLICATION_PACKAGES = List.of(
        "io.taskmigo.web..",
        "io.taskmigo.worker..",
        "io.taskmigo.migration..",
        "io.taskmigo.rest..",
        "io.taskmigo.internal.."
    );
    private static final Set<String> APPLICATION_ALLOWED_SPRING_TYPES = Set.of(
        "org.springframework.transaction.annotation.Transactional",
        "org.springframework.transaction.annotation.Propagation",
        "org.springframework.transaction.annotation.Isolation"
    );

    private OnionArchitectureRules() {}

    /// Describes the responsibility packages of one reusable bounded context.
    ///
    /// @param rootPackage root package of the bounded context
    /// @param applicationPackages packages that contain application orchestration or application-owned abstractions
    /// @param persistencePackages packages explicitly allowed to own JPA or Spring Data dependencies
    /// @param frameworkConfigurationPackages packages that own framework wiring/composition
    /// @param additionalDomainForbiddenPackages additional outward implementation packages forbidden from domain code
    /// @param additionalApplicationForbiddenPackages additional outward implementation packages forbidden from
    /// application code
    public record Context(
        String rootPackage,
        List<String> applicationPackages,
        List<String> persistencePackages,
        List<String> frameworkConfigurationPackages,
        List<String> additionalDomainForbiddenPackages,
        List<String> additionalApplicationForbiddenPackages
    ) {
        public Context {
            rootPackage = Objects.requireNonNull(rootPackage);
            applicationPackages = List.copyOf(applicationPackages);
            persistencePackages = List.copyOf(persistencePackages);
            frameworkConfigurationPackages = List.copyOf(frameworkConfigurationPackages);
            additionalDomainForbiddenPackages = List.copyOf(additionalDomainForbiddenPackages);
            additionalApplicationForbiddenPackages = List.copyOf(additionalApplicationForbiddenPackages);
        }
    }

    /// Checks production classes against the shared DDD and Onion dependency baseline.
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

    /// Checks tactical domain and application tests remain independent from outward persistence and Spring test
    /// infrastructure.
    ///
    /// @param context bounded-context package configuration
    public static void checkTacticalTests(Context context) {
        JavaClasses classes = new ClassFileImporter().importPackages(context.rootPackage());

        for (ArchRule rule : tacticalTestRules(context)) {
            rule.check(classes);
        }
    }

    static List<ArchRule> productionRules(Context context) {
        ArrayList<ArchRule> rules = new ArrayList<>();
        rules.add(domainRule(context));
        if (!context.applicationPackages().isEmpty()) {
            rules.add(applicationRule(context));
            rules.add(applicationContractRule(context));
        }
        rules.add(moduleIsolationRule(context));
        rules.add(persistenceFrameworkContainmentRule(context));
        return List.copyOf(rules);
    }

    static List<ArchRule> tacticalTestRules(Context context) {
        ArrayList<ArchRule> rules = new ArrayList<>();
        rules.add(domainTestRule(context));
        if (!context.applicationPackages().isEmpty()) {
            rules.add(applicationTestRule(context));
        }
        return List.copyOf(rules);
    }

    static ArchRule domainRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.rootPackage() + "..domain..")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(domainForbiddenPackages(context))
            .allowEmptyShould(true)
            .as("domain packages depend only inward and remain framework neutral");
    }

    static ArchRule applicationRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.applicationPackages().toArray(String[]::new))
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .dependOnClassesThat(applicationForbiddenDependencyPredicate(context))
            .allowEmptyShould(true)
            .as(
                "application code allows only approved Spring transaction declaration types and remains independent from outward implementations"
            );
    }

    static ArchRule applicationContractRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.applicationPackages().toArray(String[]::new))
            .and()
            .doNotHaveSimpleName("package-info")
            .and()
            .areInterfaces()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .allowEmptyShould(true)
            .as("application interfaces remain framework neutral");
    }

    static ArchRule moduleIsolationRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.rootPackage() + "..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(EXECUTABLE_APPLICATION_PACKAGES.toArray(String[]::new))
            .allowEmptyShould(true)
            .as("reusable modules never depend back on executable applications");
    }

    static ArchRule persistenceFrameworkContainmentRule(Context context) {
        if (context.persistencePackages().isEmpty()) {
            return noClasses()
                .that()
                .resideInAnyPackage(context.rootPackage() + "..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.data..", "jakarta.persistence..")
                .allowEmptyShould(true)
                .as("JPA and Spring Data are absent when a context declares no persistence responsibility");
        }

        return noClasses()
            .that()
            .resideOutsideOfPackages(context.persistencePackages().toArray(String[]::new))
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.data..", "jakarta.persistence..")
            .allowEmptyShould(true)
            .as("JPA and Spring Data stay inside explicit persistence responsibilities");
    }

    static ArchRule domainTestRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.rootPackage() + "..domain..")
            .and()
            .haveSimpleNameEndingWith("Test")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .allowEmptyShould(true)
            .as("domain tests remain framework neutral");
    }

    static ArchRule applicationTestRule(Context context) {
        return noClasses()
            .that()
            .resideInAnyPackage(context.applicationPackages().toArray(String[]::new))
            .and()
            .haveSimpleNameEndingWith("Test")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(applicationTestForbiddenPackages(context))
            .allowEmptyShould(true)
            .as("application tests remain independent from outward implementations and Spring test contexts");
    }

    private static DescribedPredicate<JavaClass> applicationForbiddenDependencyPredicate(Context context) {
        DescribedPredicate<JavaClass> forbiddenPackages = JavaClass.Predicates.resideInAnyPackage(
            applicationForbiddenPackages(context)
        );
        DescribedPredicate<JavaClass> unapprovedSpringType = new DescribedPredicate<>(
            "be a Spring type outside the approved application transaction declaration allowlist"
        ) {
            @Override
            public boolean test(JavaClass input) {
                return (
                    input.getName().startsWith("org.springframework.") &&
                    !APPLICATION_ALLOWED_SPRING_TYPES.contains(input.getName())
                );
            }
        };
        return forbiddenPackages.or(unapprovedSpringType);
    }

    private static String[] domainForbiddenPackages(Context context) {
        ArrayList<String> packages = new ArrayList<>();
        packages.addAll(context.applicationPackages());
        packages.addAll(context.persistencePackages());
        packages.addAll(context.frameworkConfigurationPackages());
        packages.addAll(context.additionalDomainForbiddenPackages());
        packages.addAll(EXECUTABLE_APPLICATION_PACKAGES);
        packages.add("org.springframework..");
        packages.add("jakarta.persistence..");
        return packages.toArray(String[]::new);
    }

    private static String[] applicationForbiddenPackages(Context context) {
        ArrayList<String> packages = new ArrayList<>();
        packages.addAll(context.persistencePackages());
        packages.addAll(context.frameworkConfigurationPackages());
        packages.addAll(context.additionalApplicationForbiddenPackages());
        packages.addAll(EXECUTABLE_APPLICATION_PACKAGES);
        packages.add("jakarta.persistence..");
        return packages.toArray(String[]::new);
    }

    private static String[] applicationTestForbiddenPackages(Context context) {
        ArrayList<String> packages = new ArrayList<>();
        packages.addAll(context.persistencePackages());
        packages.addAll(context.frameworkConfigurationPackages());
        packages.addAll(context.additionalApplicationForbiddenPackages());
        packages.addAll(EXECUTABLE_APPLICATION_PACKAGES);
        packages.add("org.springframework.data..");
        packages.add("jakarta.persistence..");
        packages.add("org.springframework.boot.test.context..");
        packages.add("org.springframework.test.context..");
        return packages.toArray(String[]::new);
    }
}
