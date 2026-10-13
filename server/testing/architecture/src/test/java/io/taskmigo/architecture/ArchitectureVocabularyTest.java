package io.taskmigo.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchitectureVocabularyTest {

    @Test
    @DisplayName("does not expose obsolete Hexagonal architecture vocabulary")
    void shouldNotExposeHexagonalVocabulary() {
        JavaClasses classes = new ClassFileImporter().importPackages("io.taskmigo.architecture");

        List<String> obsoleteTypes = classes.stream()
            .map(JavaClass::getName)
            .filter(name -> name.startsWith("io.taskmigo.architecture."))
            .filter(name -> name.contains("Hexagonal"))
            .toList();

        assertThat(obsoleteTypes).isEmpty();
    }
}
