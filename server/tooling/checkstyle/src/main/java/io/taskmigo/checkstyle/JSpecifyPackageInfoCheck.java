package io.taskmigo.checkstyle;

import com.puppycrawl.tools.checkstyle.GlobalStatefulCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractFileSetCheck;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.FileText;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Enforces package-level JSpecify nullness metadata for every Java package.
///
/// Main source packages must declare a local `package-info.java`. Other source sets may reuse
/// metadata from a main source package with the same package name, avoiding duplicate
/// `package-info.class` files that can hide runtime package annotations.
@GlobalStatefulCheck
public final class JSpecifyPackageInfoCheck extends AbstractFileSetCheck {

    static final String MSG_MISSING_PACKAGE_INFO = "jspecify.packageInfo";
    static final String MSG_MISSING_NULL_MARKED = "jspecify.nullMarked";

    private static final String PACKAGE_INFO_FILE_NAME = "package-info.java";
    private static final int MAIN_SOURCE_ROOT_SEARCH_DEPTH = 8;
    private static final Pattern NULL_MARKED_ANNOTATION = Pattern.compile("(?m)^\\s*@NullMarked\\s*$");
    private static final Pattern NULL_MARKED_IMPORT = Pattern.compile(
        "(?m)^\\s*import\\s+org\\.jspecify\\.annotations\\.NullMarked\\s*;\\s*$"
    );

    private final Set<File> directoriesChecked = new HashSet<>();
    private Optional<Path> indexedRepositoryRoot = Optional.empty();
    private Set<Path> mainSourceRoots = Set.of();

    public JSpecifyPackageInfoCheck() {
        setFileExtensions("java");
    }

    @Override
    protected void processFiltered(File file, FileText fileText) throws CheckstyleException {
        if ("module-info.java".equals(file.getName())) {
            return;
        }

        Path directory;
        try {
            directory = file.getCanonicalFile().toPath().getParent();
        } catch (IOException exception) {
            throw new CheckstyleException("Unable to resolve Java source directory for " + file.getPath(), exception);
        }

        if (directory == null) {
            throw new CheckstyleException("Java source must have a parent directory: " + file.getPath());
        }

        if (directoriesChecked.add(directory.toFile()) && !hasPackageInfo(directory)) {
            log(1, MSG_MISSING_PACKAGE_INFO);
        }

        if (PACKAGE_INFO_FILE_NAME.equals(file.getName()) && !isNullMarked(fileText)) {
            log(1, MSG_MISSING_NULL_MARKED);
        }
    }

    private boolean hasPackageInfo(Path packageDirectory) throws CheckstyleException {
        if (Files.isRegularFile(packageDirectory.resolve(PACKAGE_INFO_FILE_NAME))) {
            return true;
        }

        Optional<SourceLocation> sourceLocation = sourceLocation(packageDirectory);
        if (sourceLocation.isEmpty() || "main".equals(sourceLocation.orElseThrow().sourceSet())) {
            return false;
        }

        SourceLocation location = sourceLocation.orElseThrow();
        return mainSourceRoots(location.repositoryRoot())
            .stream()
            .map(root -> root.resolve(location.packagePath()).resolve(PACKAGE_INFO_FILE_NAME))
            .anyMatch(Files::isRegularFile);
    }

    private Set<Path> mainSourceRoots(Path repositoryRoot) throws CheckstyleException {
        if (!indexedRepositoryRoot.filter(repositoryRoot::equals).isPresent()) {
            indexedRepositoryRoot = Optional.of(repositoryRoot);
            try (
                Stream<Path> paths = Files.find(
                    repositoryRoot,
                    MAIN_SOURCE_ROOT_SEARCH_DEPTH,
                    (path, attributes) -> attributes.isDirectory() && isMainJavaSourceRoot(path)
                )
            ) {
                mainSourceRoots = paths.collect(Collectors.toUnmodifiableSet());
            } catch (IOException exception) {
                throw new CheckstyleException(
                    "Unable to index main Java source roots below " + repositoryRoot,
                    exception
                );
            }
        }
        return mainSourceRoots;
    }

    private static boolean isMainJavaSourceRoot(Path path) {
        Path sourceSetDirectory = path.getParent();
        Path sourceDirectory = sourceSetDirectory == null ? null : sourceSetDirectory.getParent();
        return (
            "java".equals(fileName(path)) &&
            sourceSetDirectory != null &&
            "main".equals(fileName(sourceSetDirectory)) &&
            sourceDirectory != null &&
            "src".equals(fileName(sourceDirectory))
        );
    }

    private static Optional<SourceLocation> sourceLocation(Path packageDirectory) {
        for (Path candidate = packageDirectory; candidate != null; candidate = candidate.getParent()) {
            Path sourceSetDirectory = candidate.getParent();
            Path sourceDirectory = sourceSetDirectory == null ? null : sourceSetDirectory.getParent();
            if (
                "java".equals(fileName(candidate)) &&
                sourceSetDirectory != null &&
                sourceDirectory != null &&
                "src".equals(fileName(sourceDirectory))
            ) {
                Optional<Path> repositoryRoot = repositoryRoot(sourceDirectory.getParent());
                if (repositoryRoot.isPresent()) {
                    return Optional.of(
                        new SourceLocation(
                            fileName(sourceSetDirectory),
                            candidate.relativize(packageDirectory),
                            repositoryRoot.orElseThrow()
                        )
                    );
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> repositoryRoot(Path start) {
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static String fileName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private static boolean isNullMarked(FileText fileText) {
        CharSequence source = fileText.getFullText();
        return NULL_MARKED_ANNOTATION.matcher(source).find() && NULL_MARKED_IMPORT.matcher(source).find();
    }

    private record SourceLocation(String sourceSet, Path packagePath, Path repositoryRoot) {}
}
