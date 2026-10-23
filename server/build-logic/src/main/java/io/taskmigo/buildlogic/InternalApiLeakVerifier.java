package io.taskmigo.buildlogic;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.jvm.tasks.Jar;

final class InternalApiLeakVerifier {

    private static final Pattern JDEPS_PACKAGE_DEPENDENCY_PATTERN = Pattern.compile(
        "^\\s*([A-Za-z_][\\w.]*)\\s+->\\s+([A-Za-z_][\\w.]*)\\s+.*$"
    );

    private InternalApiLeakVerifier() {}

    static void verify(Project rootProject) {
        Set<String> internalPackages = JavaPackages.internalPackages(rootProject);
        ToolProvider jdeps = ToolProvider.findFirst("jdeps").orElseThrow(() ->
            new GradleException("The JDK jdeps tool is required to verify internal API boundaries")
        );
        List<String> violations = new ArrayList<>();

        rootProject
            .getSubprojects()
            .stream()
            .filter(InternalModuleBoundariesPlugin::isProductionModule)
            .sorted((left, right) -> left.getPath().compareTo(right.getPath()))
            .forEach(module -> verifyModule(module, internalPackages, jdeps, violations));

        if (!violations.isEmpty()) {
            throw new GradleException(
                "Public APIs must not expose internal module types:\n - " + String.join("\n - ", violations)
            );
        }
    }

    private static void verifyModule(
        Project module,
        Set<String> internalPackages,
        ToolProvider jdeps,
        List<String> violations
    ) {
        Set<String> publicPackages = JavaPackages.publicApiPackages(module);
        if (publicPackages.isEmpty()) {
            return;
        }

        String includePattern = publicPackages
            .stream()
            .sorted()
            .map(Pattern::quote)
            .collect(Collectors.joining("|", "^(?:", ")$"));
        File jarFile = module.getTasks().named("jar", Jar.class).get().getArchiveFile().get().getAsFile();
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        int exitCode;

        try (PrintWriter stdoutWriter = new PrintWriter(stdout); PrintWriter stderrWriter = new PrintWriter(stderr)) {
            exitCode = jdeps.run(
                stdoutWriter,
                stderrWriter,
                "--api-only",
                "--ignore-missing-deps",
                "--no-recursive",
                "-include",
                includePattern,
                "-verbose:package",
                jarFile.getAbsolutePath()
            );
        }

        if (exitCode != 0) {
            throw new GradleException(
                "jdeps failed for " + module.getPath() + " (" + jarFile.getName() + "):\n" + stderr.toString().trim()
            );
        }

        stdout
            .toString()
            .lines()
            .forEach(line -> collectViolation(module, line, publicPackages, internalPackages, violations));
    }

    private static void collectViolation(
        Project module,
        String line,
        Set<String> publicPackages,
        Set<String> internalPackages,
        List<String> violations
    ) {
        Matcher matcher = JDEPS_PACKAGE_DEPENDENCY_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return;
        }

        String sourcePackage = matcher.group(1);
        String targetPackage = matcher.group(2);
        if (
            publicPackages.contains(sourcePackage) &&
            JavaPackages.isOwnedByInternalModule(targetPackage, internalPackages)
        ) {
            violations.add(module.getPath() + ": " + sourcePackage + " -> " + targetPackage);
        }
    }
}
