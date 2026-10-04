package io.taskmigo.authorization.object.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationFieldBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationOperator;
import io.taskmigo.authorization.object.StaticObjectAuthorizationBinding;
import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.language.Field;
import io.taskmigo.language.FieldId;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObjectAuthorizationExpressionValidatorTest {

    private static final ResourceType TYPE = ResourceType.of("test:object");
    private static final FieldId AMOUNT = FieldId.of("field:test:object:amount");

    /**
     * Verifies that Object Authorization validates operators through the semantic FieldId execution binding.
     *
     * Given: a numeric schema field and a binding that permits greater-than.
     * Expect: validation accepts the expression without consulting its display path.
     */
    @Test
    @DisplayName("accepts a supported operator through a semantic field binding")
    void shouldAcceptOperatorWhenBindingSupportsResolvedFieldId() {
        // Arrange
        ObjectAuthorizationBinding<TestObject> binding = binding(Set.of(ObjectAuthorizationOperator.GT));
        ObjectAuthorizationExpression expression = new ObjectAuthorizationExpression.Binary(
            ObjectAuthorizationExpression.BinaryOperator.GREATER,
            reference(AMOUNT),
            new ObjectAuthorizationExpression.Literal(18)
        );

        // Act + Assert
        assertThatCode(() ->
            ObjectAuthorizationExpressionValidator.validate(expression, binding)
        ).doesNotThrowAnyException();
    }

    /**
     * Verifies that an unsupported execution operator fails closed rather than using a source path fallback.
     *
     * Given: a resolved field whose binding permits equality only.
     * Expect: a greater-than policy is rejected.
     */
    @Test
    @DisplayName("rejects an operator unsupported by the field binding")
    void shouldRejectOperatorWhenBindingDoesNotSupportIt() {
        // Arrange
        ObjectAuthorizationBinding<TestObject> binding = binding(Set.of(ObjectAuthorizationOperator.EQ));
        ObjectAuthorizationExpression expression = new ObjectAuthorizationExpression.Binary(
            ObjectAuthorizationExpression.BinaryOperator.GREATER,
            reference(AMOUNT),
            new ObjectAuthorizationExpression.Literal(18)
        );

        // Act + Assert
        assertThatThrownBy(() -> ObjectAuthorizationExpressionValidator.validate(expression, binding))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("operator is not supported for object field");
    }

    /**
     * Verifies that a same-display-path reference with a different semantic identity cannot use another binding.
     *
     * Given: a binding for `amount` and a reference with a foreign FieldId.
     * Expect: validation rejects the identity rather than matching the path.
     */
    @Test
    @DisplayName("rejects a field id that is not bound even when its path matches")
    void shouldRejectFieldWhenOnlyPathWouldMatch() {
        // Arrange
        ObjectAuthorizationBinding<TestObject> binding = binding(Set.of(ObjectAuthorizationOperator.EQ));
        ObjectAuthorizationExpression expression = new ObjectAuthorizationExpression.Binary(
            ObjectAuthorizationExpression.BinaryOperator.EQUAL,
            reference(FieldId.of("field:other:amount")),
            new ObjectAuthorizationExpression.Literal(18)
        );

        // Act + Assert
        assertThatThrownBy(() -> ObjectAuthorizationExpressionValidator.validate(expression, binding))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("object field identity is unknown");
    }

    private static ObjectAuthorizationBinding<TestObject> binding(Set<ObjectAuthorizationOperator> operators) {
        ResourceSchema schema = ResourceSchema.of(
            TYPE,
            List.of(new Field(AMOUNT, FieldPath.parse("amount"), LanguageType.Scalar.NUMBER, false))
        );
        return new StaticObjectAuthorizationBinding<>(
            TestObject.class,
            schema,
            List.of(new ObjectAuthorizationFieldBinding(AMOUNT, "amount", Number.class, operators))
        );
    }

    private static ObjectAuthorizationExpression.Reference reference(FieldId id) {
        return new ObjectAuthorizationExpression.Reference("object", List.of("amount"), id);
    }

    private static final class TestObject {}
}
