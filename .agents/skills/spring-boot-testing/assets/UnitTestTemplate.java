package com.workastra.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExampleService}.
 *
 * Scope: tests business logic in isolation. All collaborators (repositories,
 * external clients) are mocked with Mockito — no Spring context, no database.
 * Use this template for service-layer / component-layer classes that have
 * logic worth verifying in isolation.
 *
 * Replace ExampleService / ExampleRepository / ExampleRequest / Example with
 * the real classes under test, and adapt the @Nested groups to the actual
 * methods being tested.
 */
@ExtendWith(MockitoExtension.class)
class ExampleServiceTest {

    @Mock
    private ExampleRepository exampleRepository;

    @InjectMocks
    private ExampleService exampleService;

    @Nested
    @DisplayName("createExample()")
    class CreateExample {

        /**
         * Verifies that creating an example with a valid request persists it
         * and returns the persisted entity to the caller.
         *
         * Given: a valid ExampleRequest with name = "name".
         * Expect: repository.save() is invoked exactly once, and the returned
         * Example has id = 1 and name = "name".
         */
        @Test
        @DisplayName("should save and return example when input is valid")
        void shouldSaveAndReturnExampleWhenInputIsValid() {
            // Arrange
            var request = new ExampleRequest("name");
            var saved = new Example(1L, "name");
            when(exampleRepository.save(any())).thenReturn(saved);

            // Act
            var result = exampleService.createExample(request);

            // Assert
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("name");
            verify(exampleRepository).save(any());
        }

        /**
         * Verifies that input validation rejects blank names before the
         * repository is ever touched.
         *
         * Given: an ExampleRequest with name = " " (blank).
         * Expect: an IllegalArgumentException is thrown with a message
         * mentioning "name", and exampleRepository.save() is never called.
         */
        @Test
        @DisplayName("should throw IllegalArgumentException when name is blank")
        void shouldThrowIllegalArgumentExceptionWhenNameIsBlank() {
            // Arrange
            var request = new ExampleRequest(" ");

            // Act + Assert
            assertThatThrownBy(() -> exampleService.createExample(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        /**
         * Verifies that looking up a non-existent example surfaces "not
         * found" as an empty Optional rather than throwing, so callers can
         * decide how to handle it (e.g. map to 404 at the controller layer).
         *
         * Given: repository.findById(99L) returns Optional.empty().
         * Expect: exampleService.findById(99L) returns Optional.empty().
         */
        @Test
        @DisplayName("should return empty when example does not exist")
        void shouldReturnEmptyWhenExampleDoesNotExist() {
            // Arrange
            when(exampleRepository.findById(99L)).thenReturn(java.util.Optional.empty());

            // Act
            var result = exampleService.findById(99L);

            // Assert
            assertThat(result).isEmpty();
        }
    }
}
