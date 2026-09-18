package io.taskmigo.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TypeDescriptorTest {

    /**
     * Verifies that parameterized contract types retain their raw type and element type without
     * depending on a framework reflection API.
     *
     * Given: a descriptor for `List<String>`.
     * Expect: its raw type is `List`, its only type argument is `String`, and its identity includes
     * both types.
     */
    @Test
    @DisplayName("should retain generic arguments when descriptor is parameterized")
    void shouldRetainGenericArgumentsWhenDescriptorIsParameterized() {
        // Arrange
        TypeDescriptor descriptor = TypeDescriptor.parameterized(List.class, TypeDescriptor.of(String.class));

        // Act
        String identity = descriptor.identity();

        // Assert
        assertThat(descriptor.rawType()).isEqualTo(List.class);
        assertThat(descriptor.typeArguments()).containsExactly(TypeDescriptor.of(String.class));
        assertThat(identity).isEqualTo("java.util.List<java.lang.String>");
    }
}
