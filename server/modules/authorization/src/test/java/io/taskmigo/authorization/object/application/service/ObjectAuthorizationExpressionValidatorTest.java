package io.taskmigo.authorization.object.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationField;
import io.taskmigo.authorization.object.ObjectAuthorizationOperator;
import io.taskmigo.authorization.object.ObjectAuthorizationPath;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.foundation.TypeDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ObjectAuthorizationExpressionValidatorTest {

    /**
     * Verifies that nested expression forms cannot hide a field from an outer Object Authorization operator check.
     *
     * Given: arithmetic, length, membership, and quantifier expressions whose inner operator is allowed while the
     * outer operator is forbidden.
     * Expect: every expression is rejected against the field allow-list.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("nestedOperatorCases")
    @DisplayName("should reject a forbidden outer operator when a nested expression hides the field")
    void shouldRejectForbiddenOuterOperatorWhenNestedExpressionHidesField(ValidationCase testCase) {
        // Arrange
        ObjectAuthorizationSchema<TestObject> schema = schema(
            testCase.path(),
            testCase.type(),
            testCase.innerOperators()
        );

        // Act + Assert
        assertRejected(testCase.expression(), schema);
    }

    /**
     * Verifies that nested attribution preserves fields that allow every participating Object Authorization operator.
     *
     * Given: arithmetic, length, membership, and quantifier expressions whose fields allow both the inner and outer
     * semantic operators.
     * Expect: every expression is accepted.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("nestedOperatorCases")
    @DisplayName("should accept nested operators when the field allows every participating operator")
    void shouldAcceptNestedOperatorsWhenFieldAllowsEveryParticipatingOperator(ValidationCase testCase) {
        // Arrange
        ObjectAuthorizationSchema<TestObject> schema = schema(
            testCase.path(),
            testCase.type(),
            testCase.allOperators()
        );

        // Act + Assert
        assertThatCode(() ->
            ObjectAuthorizationExpressionValidator.validate(testCase.expression(), schema)
        ).doesNotThrowAnyException();
    }

    /**
     * Verifies that each unary expression is attributed to its matching Object Authorization field operator.
     *
     * Given: a field allow-list containing exactly the operator corresponding to NOT, PLUS, or MINUS.
     * Expect: validation accepts the unary expression without substituting another unary operator.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ObjectAuthorizationExpression.UnaryOperator.class)
    @DisplayName("should accept a unary expression when its matching operator is allowed")
    void shouldAcceptUnaryExpressionWhenMatchingOperatorIsAllowed(
        ObjectAuthorizationExpression.UnaryOperator expressionOperator
    ) {
        // Arrange
        ObjectAuthorizationOperator allowedOperator = ObjectAuthorizationOperator.valueOf(expressionOperator.name());
        ObjectAuthorizationSchema<TestObject> schema = schema("amount", Integer.class, Set.of(allowedOperator));
        ObjectAuthorizationExpression expression = new ObjectAuthorizationExpression.Unary(
            expressionOperator,
            reference("amount")
        );

        // Act + Assert
        assertThatCode(() ->
            ObjectAuthorizationExpressionValidator.validate(expression, schema)
        ).doesNotThrowAnyException();
    }

    /**
     * Verifies that unary PLUS cannot borrow permission from unary NOT.
     *
     * Given: an amount field that allows NOT but does not allow PLUS.
     * Expect: validation rejects a unary PLUS expression over that field.
     */
    @Test
    @DisplayName("should reject unary plus when only logical not is allowed")
    void shouldRejectUnaryPlusWhenOnlyLogicalNotIsAllowed() {
        // Arrange
        ObjectAuthorizationSchema<TestObject> schema = schema(
            "amount",
            Integer.class,
            Set.of(ObjectAuthorizationOperator.NOT)
        );
        ObjectAuthorizationExpression expression = new ObjectAuthorizationExpression.Unary(
            ObjectAuthorizationExpression.UnaryOperator.PLUS,
            reference("amount")
        );

        // Act + Assert
        assertRejected(expression, schema);
    }

    private static Stream<ValidationCase> nestedOperatorCases() {
        return Stream.of(
            new ValidationCase(
                "arithmetic",
                "amount",
                Integer.class,
                Set.of(ObjectAuthorizationOperator.ADD),
                Set.of(ObjectAuthorizationOperator.ADD, ObjectAuthorizationOperator.GT),
                new ObjectAuthorizationExpression.Binary(
                    ObjectAuthorizationExpression.BinaryOperator.GREATER,
                    new ObjectAuthorizationExpression.Binary(
                        ObjectAuthorizationExpression.BinaryOperator.ADD,
                        reference("amount"),
                        new ObjectAuthorizationExpression.Literal(0)
                    ),
                    new ObjectAuthorizationExpression.Literal(18)
                )
            ),
            new ValidationCase(
                "length",
                "values",
                List.class,
                Set.of(ObjectAuthorizationOperator.LENGTH),
                Set.of(ObjectAuthorizationOperator.LENGTH, ObjectAuthorizationOperator.GT),
                new ObjectAuthorizationExpression.Binary(
                    ObjectAuthorizationExpression.BinaryOperator.GREATER,
                    new ObjectAuthorizationExpression.Length(reference("values")),
                    new ObjectAuthorizationExpression.Literal(0)
                )
            ),
            new ValidationCase(
                "membership",
                "values",
                List.class,
                Set.of(ObjectAuthorizationOperator.IN),
                Set.of(ObjectAuthorizationOperator.IN, ObjectAuthorizationOperator.EQ),
                new ObjectAuthorizationExpression.Binary(
                    ObjectAuthorizationExpression.BinaryOperator.EQUAL,
                    new ObjectAuthorizationExpression.Binary(
                        ObjectAuthorizationExpression.BinaryOperator.IN,
                        new ObjectAuthorizationExpression.Literal(1),
                        reference("values")
                    ),
                    new ObjectAuthorizationExpression.Literal(true)
                )
            ),
            new ValidationCase(
                "quantifier",
                "values",
                List.class,
                Set.of(ObjectAuthorizationOperator.ANY),
                Set.of(ObjectAuthorizationOperator.ANY, ObjectAuthorizationOperator.EQ),
                new ObjectAuthorizationExpression.Binary(
                    ObjectAuthorizationExpression.BinaryOperator.EQUAL,
                    new ObjectAuthorizationExpression.Quantifier(
                        ObjectAuthorizationExpression.QuantifierOperator.ANY,
                        reference("values"),
                        "item",
                        new ObjectAuthorizationExpression.Binary(
                            ObjectAuthorizationExpression.BinaryOperator.GREATER,
                            new ObjectAuthorizationExpression.Reference("item", List.of()),
                            new ObjectAuthorizationExpression.Literal(0)
                        )
                    ),
                    new ObjectAuthorizationExpression.Literal(true)
                )
            )
        );
    }

    private static void assertRejected(
        ObjectAuthorizationExpression expression,
        ObjectAuthorizationSchema<TestObject> schema
    ) {
        assertThatThrownBy(() -> ObjectAuthorizationExpressionValidator.validate(expression, schema))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("operator is not supported for object path");
    }

    private static ObjectAuthorizationExpression.Reference reference(String path) {
        return new ObjectAuthorizationExpression.Reference("object", List.of(path));
    }

    private static ObjectAuthorizationSchema<TestObject> schema(
        String path,
        Class<?> type,
        Set<ObjectAuthorizationOperator> operators
    ) {
        ObjectAuthorizationField field = new ObjectAuthorizationField(
            ObjectAuthorizationPath.of(path),
            TypeDescriptor.of(type),
            false,
            operators
        );
        return new ObjectAuthorizationSchema<>() {
            @Override
            public Class<TestObject> objectType() {
                return TestObject.class;
            }

            @Override
            public Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath objectPath) {
                return field.path().equals(objectPath) ? Optional.of(field) : Optional.empty();
            }

            @Override
            public Collection<ObjectAuthorizationField> fields() {
                return List.of(field);
            }
        };
    }

    private record ValidationCase(
        String name,
        String path,
        Class<?> type,
        Set<ObjectAuthorizationOperator> innerOperators,
        Set<ObjectAuthorizationOperator> allOperators,
        ObjectAuthorizationExpression expression
    ) {
        @Override
        public String toString() {
            return this.name;
        }
    }

    private static final class TestObject {}
}
