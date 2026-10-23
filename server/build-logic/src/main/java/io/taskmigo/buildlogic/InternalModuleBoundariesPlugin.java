package io.taskmigo.buildlogic;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

public final class InternalModuleBoundariesPlugin implements Plugin<Project> {

    private static final String INTERNAL_MODULE_PROJECT_PREFIX = ":modules:internal:";
    private static final Pattern JAVA_PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)\\s*;");
    private static final Pattern JAVA_IMPORT_PATTERN = Pattern.compile(
        "(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_][\\w.]*(?:\\.\\*)?)\\s*;"
    );
    private static final Pattern MODULITH_API_ANNOTATION_PATTERN = Pattern.compile(
        "@(?:[A-Za-z_][\\w.]*\\.)?(?:ApplicationModule|NamedInterface)\\b"
    );
    private static final Pattern JDEPS_PACKAGE_DEPENDENCY_PATTERN = Pattern.compile(
        "^\\s*([A-Za-z_][\\w.]*)\\s+->\\s+([A-Za-z_][\\w.]*)\\s+.*$"
    );

    @Override
    public void apply(Project project) {
        if (project != project.getRootProject()) {
            throw new GradleException("io.taskmigo.internal-module-boundaries must be applied to the root project");
        }

        TaskProvider<Task> dependencyCheck = registerInternalProjectDependenciesCheck(project);
        TaskProvider<Task> importCheck = registerApplicationInternalImportsCheck(project);
        TaskProvider<Task> apiCheck = registerInternalApiBoundariesCheck(project);
        TaskProvider<Task> boundaryCheck = project.getTasks().register("verifyInternalModuleBoundaries", task -> {
            task.setGroup("verification");
            task.setDescription("Verifies all internal server module boundaries.");
            task.dependsOn(dependencyCheck, importCheck, apiCheck);
        });

        project.getPluginManager().withPlugin("base", ignored ->
            project
                .getTasks()
                .named("check")
                .configure(task -> task.dependsOn(boundaryCheck))
        );
    }

    private static TaskProvider<Task> registerInternalProjectDependenciesCheck(Project project) {
        return project.getTasks().register("verifyInternalProjectDependencies", task -> {
            task.setGroup("verification");
            task.setDescription("Rejects forbidden direct Gradle dependencies on internal server modules.");
            task.doLast(ignored -> {
                List<String> violations = new ArrayList<>();

                project
                    .getSubprojects()
                    .stream()
                    .sorted((left, right) -> left.getPath().compareTo(right.getPath()))
                    .forEach(sourceProject -> {
                        boolean isApp = sourceProject.getPath().startsWith(":apps:");
                        boolean isProductionModule =
                            sourceProject.getPath().startsWith(":modules:") &&
                            !sourceProject.getPath().startsWith(INTERNAL_MODULE_PROJECT_PREFIX);
                        if (!isApp && !isProductionModule) {
                            return;
                        }

                        sourceProject
                            .getConfigurations()
                            .stream()
                            .sorted((left, right) -> left.getName().compareTo(right.getName()))
                            .forEach(configuration -> {
                                if (
                                    isProductionModule &&
                                    !Set.of("api", "compileOnlyApi").contains(configuration.getName())
                                ) {
                                    return;
                                }
                                configuration
                                    .getDependencies()
                                    .withType(ProjectDependency.class)
                                    .forEach(dependency -> {
                                        if (dependency.getPath().startsWith(INTERNAL_MODULE_PROJECT_PREFIX)) {
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
                                        }
                                    });
                            });
                    });

                failIfViolations(violations, "Internal module dependency boundary violations:");
            });
        });
    }

    private static TaskProvider<Task> registerApplicationInternalImportsCheck(Project project) {
        return project.getTasks().register("verifyApplicationInternalImports", task -> {
            task.setGroup("verification");
            task.setDescription("Rejects production app source imports from internal server modules.");
            task.doLast(ignored -> {
                Set<String> internalPackages = internalPackages(project);
                List<String> violations = new ArrayList<>();

                project
                    .getSubprojects()
                    .stream()
                    .filter(application -> application.getPath().startsWith(":apps:"))
                    .sorted((left, right) -> left.getPath().compareTo(right.getPath()))
                    .forEach(application ->
                        mainJavaSources(application).forEach(source -> {
                            Matcher matcher = JAVA_IMPORT_PATTERN.matcher(readText(source));
                            while (matcher.find()) {
                                String importedName = matcher.group(1).replaceAll("\\.\\*$", "");
                                if (isOwnedByInternalModule(importedName, internalPackages)) {
                                    violations.add(relativeToRoot(project, source) + " imports " + importedName);
                                }
                            }
                        })
                    );

                failIfViolations(violations, "Applications must not import internal module packages:");
            });
        });
    }

    private static TaskProvider<Task> registerInternalApiBoundariesCheck(Project project) {
        TaskProvider<Task> verificationTask = project.getTasks().register("verifyInternalApiBoundaries", task -> {
            task.setGroup("verification");
            task.setDescription(
                "Rejects internal module types from public application APIs and Modulith named interfaces."
            );
            task.doLast(ignored -> verifyInternalApiBoundaries(project));
        });

        project.getGradle().projectsEvaluated(ignored -> {
            List<TaskProvider<Jar>> jarTasks = project
                .getSubprojects()
                .stream()
                .filter(
                    module ->
                        module.getPath().startsWith(":modules:") &&
                        !module.getPath().startsWith(INTERNAL_MODULE_PROJECT_PREFIX)
                )
                .filter(module -> module.getPluginManager().hasPlugin("java"))
                .map(module -> module.getTasks().named("jar", Jar.class))
                .toList();
            verificationTask.configure(task -> task.dependsOn(jarTasks));
        });

        return verificationTask;
    }

    private static void verifyInternalApiBoundaries(Project project) {
        Set<String> internalPackages = internalPackages(project);
        ToolProvider jdeps = ToolProvider.findFirst("jdeps").orElseThrow(() ->
            new GradleException("The JDK jdeps tool is required to verify internal API boundaries")
        );
        List<String> violations = new ArrayList<>();

        project
            .getSubprojects()
            .stream()
            .filter(
                module ->
                    module.getPath().startsWith(":modules:") &&
                    !module.getPath().startsWith(INTERNAL_MODULE_PROJECT_PREFIX)
            )
            .sorted((left, right) -> left.getPath().compareTo(right.getPath()))
            .forEach(module -> {
                Set<String> publicPackages = publicApiPackages(module);
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
                PrintWriter stdoutWriter = new PrintWriter(stdout);
                PrintWriter stderrWriter = new PrintWriter(stderr);
                int exitCode = jdeps.run(
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
                stdoutWriter.flush();
                stderrWriter.flush();
                if (exitCode != 0) {
                    throw new GradleException(
                        "jdeps failed for " +
                            module.getPath() +
                            " (" +
                            jarFile.getName() +
                            "):\n" +
                            stderr.toString().trim()
                    );
                }

                stdout
                    .toString()
                    .lines()
                    .forEach(line -> {
                        Matcher matcher = JDEPS_PACKAGE_DEPENDENCY_PATTERN.matcher(line);
                        if (!matcher.matches()) {
                            return;
                        }
                        String sourcePackage = matcher.group(1);
                        String targetPackage = matcher.group(2);
                        if (
                            publicPackages.contains(sourcePackage) &&
                            isOwnedByInternalModule(targetPackage, internalPackages)
                        ) {
                            violations.add(module.getPath() + ": " + sourcePackage + " -> " + targetPackage);
                        }
                    });
            });

        failIfViolations(violations, "Public APIs must not expose internal module types:");
    }

    private static Set<String> internalPackages(Project project) {
        Set<String> packages = new TreeSet<>();
        project
            .getSubprojects()
            .stream()
            .filter(module -> module.getPath().startsWith(INTERNAL_MODULE_PROJECT_PREFIX))
            .forEach(module -> packages.addAll(mainJavaPackages(module)));
        return packages;
    }

    private static Set<String> mainJavaPackages(Project project) {
        Set<String> packages = new TreeSet<>();
        mainJavaSources(project).forEach(source -> {
            Matcher matcher = JAVA_PACKAGE_PATTERN.matcher(readText(source));
            if (matcher.find()) {
                packages.add(matcher.group(1));
            }
        });
        return packages;
    }

    private static Set<String> publicApiPackages(Project project) {
        Set<String> packages = new TreeSet<>();
        mainJavaSources(project).forEach(source -> {
            String sourceText = readText(source);
            Matcher matcher = JAVA_PACKAGE_PATTERN.matcher(sourceText);
            if (!matcher.find()) {
                return;
            }
            String packageName = matcher.group(1);
            if (
                packageName.contains(".application.api") ||
                (source.getName().equals("package-info.java") &&
                    MODULITH_API_ANNOTATION_PATTERN.matcher(sourceText).find())
            ) {
                packages.add(packageName);
            }
        });
        return packages;
    }

    private static Stream<File> mainJavaSources(Project project) {
        File sourceRoot = project.file("src/main/java");
        if (!sourceRoot.isDirectory()) {
            return Stream.empty();
        }
        return project
            .fileTree(sourceRoot)
            .matching(pattern -> pattern.include("**/*.java"))
            .getFiles()
            .stream();
    }

    private static boolean isOwnedByInternalModule(String packageName, Set<String> internalPackages) {
        return internalPackages
            .stream()
            .anyMatch(
                internalPackage -> packageName.equals(internalPackage) || packageName.startsWith(internalPackage + ".")
            );
    }

    private static String readText(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException exception) {
            throw new GradleException("Failed to read " + file, exception);
        }
    }

    private static String relativeToRoot(Project rootProject, File file) {
        return rootProject.getProjectDir().toPath().relativize(file.toPath()).toString();
    }

    private static void failIfViolations(List<String> violations, String heading) {
        if (violations.isEmpty()) {
            return;
        }
        throw new GradleException(heading + "\n - " + String.join("\n - ", violations));
    }
}
