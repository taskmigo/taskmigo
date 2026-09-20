package io.taskmigo.checkstyle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.FileText;
import com.puppycrawl.tools.checkstyle.api.Violation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JSpecifyPackageInfoCheckTest {

    @Test
    @DisplayName("Reports a package that does not declare package-info.java")
    void reportsMissingPackageInfo(@TempDir Path tempDir) throws Exception {
        Path packageDirectory = Files.createDirectories(tempDir.resolve("io/taskmigo/example"));
        Path source = Files.writeString(
            packageDirectory.resolve("Example.java"),
            """
            package io.taskmigo.example;

            final class Example {}
            """,
            UTF_8
        );

        SortedSet<Violation> violations = process(configuredCheck(), source);

        assertThat(violations)
            .extracting(Violation::getKey)
            .containsExactly(JSpecifyPackageInfoCheck.MSG_MISSING_PACKAGE_INFO);
    }

    @Test
    @DisplayName("Reports a missing package-info.java only once per package")
    void reportsMissingPackageInfoOnce(@TempDir Path tempDir) throws Exception {
        Path packageDirectory = Files.createDirectories(tempDir.resolve("io/taskmigo/example"));
        Path first = Files.writeString(
            packageDirectory.resolve("First.java"),
            "package io.taskmigo.example;\nfinal class First {}\n",
            UTF_8
        );
        Path second = Files.writeString(
            packageDirectory.resolve("Second.java"),
            "package io.taskmigo.example;\nfinal class Second {}\n",
            UTF_8
        );
        JSpecifyPackageInfoCheck check = configuredCheck();

        assertThat(process(check, first))
            .extracting(Violation::getKey)
            .containsExactly(JSpecifyPackageInfoCheck.MSG_MISSING_PACKAGE_INFO);
        assertThat(process(check, second)).isEmpty();
    }

    @Test
    @DisplayName("Allows a test package to reuse main package metadata")
    void allowsTestPackageToReuseMainPackageInfo(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"test\"\n", UTF_8);
        Path mainPackage = Files.createDirectories(
            tempDir.resolve("module-a/src/main/java/io/taskmigo/example")
        );
        Files.writeString(
            mainPackage.resolve("package-info.java"),
            """
            @NullMarked
            package io.taskmigo.example;

            import org.jspecify.annotations.NullMarked;
            """,
            UTF_8
        );
        Path testPackage = Files.createDirectories(
            tempDir.resolve("module-b/src/test/java/io/taskmigo/example")
        );
        Path source = Files.writeString(
            testPackage.resolve("ExampleTest.java"),
            "package io.taskmigo.example;\nfinal class ExampleTest {}\n",
            UTF_8
        );

        assertThat(process(configuredCheck(), source)).isEmpty();
    }

    @Test
    @DisplayName("Accepts package-info.java with the JSpecify NullMarked contract")
    void acceptsNullMarkedPackageInfo(@TempDir Path tempDir) throws Exception {
        Path packageDirectory = Files.createDirectories(tempDir.resolve("io/taskmigo/example"));
        Path packageInfo = Files.writeString(
            packageDirectory.resolve("package-info.java"),
            """
            /// Example package.
            @NullMarked
            package io.taskmigo.example;

            import org.jspecify.annotations.NullMarked;
            """,
            UTF_8
        );

        assertThat(process(configuredCheck(), packageInfo)).isEmpty();
    }

    @Test
    @DisplayName("Rejects package-info.java without NullMarked")
    void rejectsPackageInfoWithoutNullMarked(@TempDir Path tempDir) throws Exception {
        Path packageDirectory = Files.createDirectories(tempDir.resolve("io/taskmigo/example"));
        Path packageInfo = Files.writeString(
            packageDirectory.resolve("package-info.java"),
            """
            /// Example package.
            package io.taskmigo.example;
            """,
            UTF_8
        );

        assertThat(process(configuredCheck(), packageInfo))
            .extracting(Violation::getKey)
            .containsExactly(JSpecifyPackageInfoCheck.MSG_MISSING_NULL_MARKED);
    }

    @Test
    @DisplayName("Rejects NullMarked imported from a non-JSpecify package")
    void rejectsNonJSpecifyNullMarked(@TempDir Path tempDir) throws Exception {
        Path packageDirectory = Files.createDirectories(tempDir.resolve("io/taskmigo/example"));
        Path packageInfo = Files.writeString(
            packageDirectory.resolve("package-info.java"),
            """
            /// Example package.
            @NullMarked
            package io.taskmigo.example;

            import example.annotations.NullMarked;
            """,
            UTF_8
        );

        assertThat(process(configuredCheck(), packageInfo))
            .extracting(Violation::getKey)
            .containsExactly(JSpecifyPackageInfoCheck.MSG_MISSING_NULL_MARKED);
    }

    private static JSpecifyPackageInfoCheck configuredCheck() throws Exception {
        JSpecifyPackageInfoCheck check = new JSpecifyPackageInfoCheck();
        check.configure(new DefaultConfiguration(JSpecifyPackageInfoCheck.class.getName()));
        return check;
    }

    private static SortedSet<Violation> process(JSpecifyPackageInfoCheck check, Path source) throws Exception {
        return check.process(source.toFile(), new FileText(source.toFile(), UTF_8.name()));
    }
}
