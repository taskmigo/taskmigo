package io.taskmigo.checkstyle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.TreeWalker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JSpecifyNullMarkedCheckTest {

    @Test
    @DisplayName("Ignores ordinary Java source files")
    void ignoresOrdinaryJavaSourceFiles(@TempDir Path tempDir) throws Exception {
        Path source = Files.writeString(
            tempDir.resolve("Example.java"),
            """
            package io.taskmigo.example;

            final class Example {}
            """,
            UTF_8
        );

        assertThat(violations(source)).isZero();
    }

    @Test
    @DisplayName("Accepts package-info.java with imported JSpecify NullMarked")
    void acceptsImportedJSpecifyNullMarked(@TempDir Path tempDir) throws Exception {
        Path packageInfo = Files.writeString(
            tempDir.resolve("package-info.java"),
            """
            @NullMarked
            package io.taskmigo.example;

            import org.jspecify.annotations.NullMarked;
            """,
            UTF_8
        );

        assertThat(violations(packageInfo)).isZero();
    }

    @Test
    @DisplayName("Accepts package-info.java with fully qualified JSpecify NullMarked")
    void acceptsFullyQualifiedJSpecifyNullMarked(@TempDir Path tempDir) throws Exception {
        Path packageInfo = Files.writeString(
            tempDir.resolve("package-info.java"),
            """
            @org.jspecify.annotations.NullMarked
            package io.taskmigo.example;
            """,
            UTF_8
        );

        assertThat(violations(packageInfo)).isZero();
    }

    @Test
    @DisplayName("Accepts package-info.java with a JSpecify annotation wildcard import")
    void acceptsJSpecifyWildcardImport(@TempDir Path tempDir) throws Exception {
        Path packageInfo = Files.writeString(
            tempDir.resolve("package-info.java"),
            """
            @NullMarked
            package io.taskmigo.example;

            import org.jspecify.annotations.*;
            """,
            UTF_8
        );

        assertThat(violations(packageInfo)).isZero();
    }

    @Test
    @DisplayName("Rejects package-info.java without NullMarked")
    void rejectsMissingNullMarked(@TempDir Path tempDir) throws Exception {
        Path packageInfo = Files.writeString(
            tempDir.resolve("package-info.java"),
            "package io.taskmigo.example;\n",
            UTF_8
        );

        assertThat(violations(packageInfo)).isOne();
    }

    @Test
    @DisplayName("Rejects NullMarked imported from a non-JSpecify package")
    void rejectsNonJSpecifyNullMarked(@TempDir Path tempDir) throws Exception {
        Path packageInfo = Files.writeString(
            tempDir.resolve("package-info.java"),
            """
            @NullMarked
            package io.taskmigo.example;

            import example.annotations.NullMarked;
            """,
            UTF_8
        );

        assertThat(violations(packageInfo)).isOne();
    }

    private static int violations(Path source) throws Exception {
        DefaultConfiguration check = new DefaultConfiguration(JSpecifyNullMarkedCheck.class.getName());
        DefaultConfiguration treeWalker = new DefaultConfiguration(TreeWalker.class.getName());
        treeWalker.addChild(check);

        DefaultConfiguration root = new DefaultConfiguration("configuration");
        root.addProperty("charset", UTF_8.name());
        root.addChild(treeWalker);

        Checker checker = new Checker();
        checker.setModuleClassLoader(Thread.currentThread().getContextClassLoader());
        try {
            checker.configure(root);
            return checker.process(List.of(source.toFile()));
        } finally {
            checker.destroy();
        }
    }
}
