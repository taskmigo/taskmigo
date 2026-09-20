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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// Enforces package-level JSpecify nullness metadata for every Java package.
///
/// Each checked package must provide a `package-info.java` file, and that file must opt into
/// JSpecify null-marked semantics with `org.jspecify.annotations.NullMarked`.
@GlobalStatefulCheck
public final class JSpecifyPackageInfoCheck extends AbstractFileSetCheck {

    static final String MSG_MISSING_PACKAGE_INFO = "jspecify.packageInfo";
    static final String MSG_MISSING_NULL_MARKED = "jspecify.nullMarked";

    private static final String PACKAGE_INFO_FILE_NAME = "package-info.java";
    private static final Pattern NULL_MARKED_ANNOTATION = Pattern.compile("(?m)^\\s*@NullMarked\\s*$");
    private static final Pattern NULL_MARKED_IMPORT = Pattern.compile(
        "(?m)^\\s*import\\s+org\\.jspecify\\.annotations\\.NullMarked\\s*;\\s*$"
    );

    private final Set<File> directoriesChecked = new HashSet<>();

    public JSpecifyPackageInfoCheck() {
        setFileExtensions("java");
    }

    @Override
    protected void processFiltered(File file, FileText fileText) throws CheckstyleException {
        if ("module-info.java".equals(file.getName())) {
            return;
        }

        File directory;
        try {
            directory = Objects.requireNonNull(
                file.getCanonicalFile().getParentFile(),
                "Java source must have a parent directory"
            );
        } catch (IOException exception) {
            throw new CheckstyleException("Unable to resolve Java source directory for " + file.getPath(), exception);
        }

        if (directoriesChecked.add(directory)) {
            Path packageInfo = directory.toPath().resolve(PACKAGE_INFO_FILE_NAME);
            if (!Files.exists(packageInfo)) {
                log(1, MSG_MISSING_PACKAGE_INFO);
            }
        }

        if (PACKAGE_INFO_FILE_NAME.equals(file.getName()) && !isNullMarked(fileText)) {
            log(1, MSG_MISSING_NULL_MARKED);
        }
    }

    private static boolean isNullMarked(FileText fileText) {
        CharSequence source = fileText.getFullText();
        return NULL_MARKED_ANNOTATION.matcher(source).find() && NULL_MARKED_IMPORT.matcher(source).find();
    }
}
