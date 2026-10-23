package io.taskmigo.buildlogic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;

final class InternalDependencyVerifier {

    private static final Set<String> EXPORTED_CONFIGURATIONS = Set.of("api", "compileOnlyApi");

    private InternalDependencyVerifier() {}

    static void verify(Project rootProject) {
        List<String> violations = new ArrayList<>();

        rootProject
            .getSubprojects()
            .stream()
            .sorted((left, right) -> left.getPath().compareTo(right.getPath()))
            .forEach(sourceProject -> {
                boolean isApp = InternalModuleBoundariesPlugin.isApp(sourceProject);
                boolean isProductionModule = InternalModuleBoundariesPlugin.isProductionModule(sourceProject);
                if (!isApp && !isProductionModule) {
                    return;
                }

                sourceProject
                    .getConfigurations()
                    .stream()
                    .sorted((left, right) -> left.getName().compareTo(right.getName()))
                    .forEach(configuration -> {
                        if (isProductionModule && !EXPORTED_CONFIGURATIONS.contains(configuration.getName())) {
                            return;
                        }

                        configuration
                            .getDependencies()
                            .withType(ProjectDependency.class)
                            .forEach(dependency -> {
                                if (
                                    !dependency
                                        .getPath()
                                        .startsWith(InternalModuleBoundariesPlugin.INTERNAL_MODULE_PROJECT_PREFIX)
                                ) {
                                    return;
                                }

                                String reason = isApp
                                    ? " (apps must not depend directly on internal modules)"
                                    : " (production modules must not export internal modules)";
                                violations.add(
                                    sourceProject.getPath() +
                                        ":" +
                                        configuration.getName() +
                                        " -> " +
                                        dependency.getPath() +
                                        reason
                                );
                            });
                    });
            });

        if (!violations.isEmpty()) {
            throw new GradleException(
                "Internal module dependency boundary violations:\n - " + String.join("\n - ", violations)
            );
        }
    }
}
