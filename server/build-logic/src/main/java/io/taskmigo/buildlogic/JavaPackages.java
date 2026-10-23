package io.taskmigo.buildlogic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

final class JavaPackages {

    private static final Pattern JAVA_PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)\\s*;");
    private static final Pattern JAVA_IMPORT_PATTERN = Pattern.compile(
        "(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_][\\w.]*(?:\\.\\*)?)\\s*;"
    );
    private static final Pattern MODULITH_API_ANNOTATION_PATTERN = Pattern.compile(
        "@(?:[A-Za-z_][\\w.]*\\.)?(?:ApplicationModule|NamedInterface)\\b"
    );

    private JavaPackages() {}

    static Set<String> internalPackages(Project rootProject) {
        Set<String> packages = new TreeSet<>();
        rootProject
            .getSubprojects()
            .stream()
            .filter(InternalModuleBoundariesPlugin::isInternalModule)
            .forEach(module -> packages.addAll(mainPackages(module)));
        return packages;
    }

    static List<String> imports(File source) {
        List<String> imports = new ArrayList<>();
        Matcher matcher = JAVA_IMPORT_PATTERN.matcher(readText(source));
        while (matcher.find()) {
            imports.add(matcher.group(1).replaceAll("\\.\\*$", ""));
        }
        return imports;
    }

    static Set<String> publicApiPackages(Project project) {
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

    static Stream<File> mainJavaSources(Project project) {
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

    static boolean isOwnedByInternalModule(String packageName, Set<String> internalPackages) {
        return internalPackages
            .stream()
            .anyMatch(
                internalPackage -> packageName.equals(internalPackage) || packageName.startsWith(internalPackage + ".")
            );
    }

    static String relativeToRoot(Project rootProject, File file) {
        return rootProject.getProjectDir().toPath().relativize(file.toPath()).toString();
    }

    private static Set<String> mainPackages(Project project) {
        Set<String> packages = new TreeSet<>();
        mainJavaSources(project).forEach(source -> {
            Matcher matcher = JAVA_PACKAGE_PATTERN.matcher(readText(source));
            if (matcher.find()) {
                packages.add(matcher.group(1));
            }
        });
        return packages;
    }

    private static String readText(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException exception) {
            throw new GradleException("Failed to read " + file, exception);
        }
    }
}
