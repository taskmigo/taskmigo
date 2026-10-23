package io.taskmigo.buildlogic;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

final class AppInternalImportVerifier {

    private AppInternalImportVerifier() {}

    static void verify(Project rootProject) {
        Set<String> internalPackages = JavaPackages.internalPackages(rootProject);
        List<String> violations = new ArrayList<>();

        rootProject
            .getSubprojects()
            .stream()
            .filter(InternalModuleBoundariesPlugin::isApp)
            .sorted((left, right) -> left.getPath().compareTo(right.getPath()))
            .forEach(application ->
                JavaPackages.mainJavaSources(application).forEach(source ->
                    collectViolations(rootProject, source, internalPackages, violations)
                )
            );

        if (!violations.isEmpty()) {
            throw new GradleException(
                "Applications must not import internal module packages:\n - " + String.join("\n - ", violations)
            );
        }
    }

    private static void collectViolations(
        Project rootProject,
        File source,
        Set<String> internalPackages,
        List<String> violations
    ) {
        JavaPackages.imports(source).forEach(importedName -> {
            if (JavaPackages.isOwnedByInternalModule(importedName, internalPackages)) {
                violations.add(JavaPackages.relativeToRoot(rootProject, source) + " imports " + importedName);
            }
        });
    }
}
