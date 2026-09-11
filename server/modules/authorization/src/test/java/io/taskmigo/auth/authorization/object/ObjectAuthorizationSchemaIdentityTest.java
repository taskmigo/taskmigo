package io.taskmigo.auth.authorization.object;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

class ObjectAuthorizationSchemaIdentityTest {

    /**
     * Verifies that Object Authorization Schema identity is independent of field and operator collection order.
     *
     * Given: equivalent schemas declared with reversed field order and operator sets.
     * Expect: both schemas produce the same canonical identity.
     */
    @Test
    @DisplayName("should canonicalize equivalent object authorization schemas")
    void shouldProduceSameIdentityWhenSchemaCollectionOrderDiffers() {
        // Arrange
        ObjectAuthorizationField name = new ObjectAuthorizationField(
            ObjectAuthorizationPath.of("name"),
            ResolvableType.forClass(String.class),
            false,
            Set.of(ObjectAuthorizationOperator.NE, ObjectAuthorizationOperator.EQ)
        );
        ObjectAuthorizationField score = new ObjectAuthorizationField(
            ObjectAuthorizationPath.of("score"),
            ResolvableType.forClass(Integer.class),
            true,
            Set.of(ObjectAuthorizationOperator.GE, ObjectAuthorizationOperator.LT)
        );

        // Act
        ObjectAuthorizationSchema<Contract> first = schema(List.of(name, score));
        ObjectAuthorizationSchema<Contract> second = schema(List.of(score, name));

        // Assert
        assertThat(first.identity()).isEqualTo(second.identity());
    }

    private static ObjectAuthorizationSchema<Contract> schema(Collection<ObjectAuthorizationField> fields) {
        List<ObjectAuthorizationField> declared = List.copyOf(fields);
        return new ObjectAuthorizationSchema<>() {
            @Override
            public Class<Contract> objectType() {
                return Contract.class;
            }

            @Override
            public Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath path) {
                return declared
                    .stream()
                    .filter(field -> field.path().equals(path))
                    .findFirst();
            }

            @Override
            public Collection<ObjectAuthorizationField> fields() {
                return declared;
            }
        };
    }

    private static final class Contract {}
}
