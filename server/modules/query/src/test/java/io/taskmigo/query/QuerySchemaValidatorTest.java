package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.query.model.QueryExpression;
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

class QuerySchemaValidatorTest {

    /**
     * Verifies that nested expression forms cannot hide a field from an outer operator permission check.
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
        QuerySchema<TestQuery> schema = schema(testCase.path(), testCase.type(), testCase.innerOperators());

        // Act + Assert
        assertRejected(testCase.expression(), schema);
    }

    /**
     * Verifies that nested operator attribution does not reject fields that allow every participating operator.
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
        QuerySchema<TestQuery> schema = schema(testCase.path(), testCase.type(), testCase.allOperators());

        // Act + Assert
        assertThatCode(() -> QuerySchemaValidator.validate(testCase.expression(), schema)).doesNotThrowAnyException();
    }

    /**
     * Verifies that each unary expression is attributed to its matching field operator.
     *
     * Given: a field allow-list containing exactly the operator corresponding to NOT, PLUS, or MINUS.
     * Expect: validation accepts the unary expression without substituting another unary operator.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(QueryExpression.UnaryOperator.class)
    @DisplayName("should accept a unary expression when its matching operator is allowed")
    void shouldAcceptUnaryExpressionWhenMatchingOperatorIsAllowed(QueryExpression.UnaryOperator expressionOperator) {
        // Arrange
        QueryOperator allowedOperator = QueryOperator.valueOf(expressionOperator.name());
        QuerySchema<TestQuery> schema = schema("amount", Integer.class, Set.of(allowedOperator));
        QueryExpression expression = new QueryExpression.Unary(expressionOperator, reference("amount"));

        // Act + Assert
        assertThatCode(() -> QuerySchemaValidator.validate(expression, schema)).doesNotThrowAnyException();
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
        QuerySchema<TestQuery> schema = schema("amount", Integer.class, Set.of(QueryOperator.NOT));
        QueryExpression expression = new QueryExpression.Unary(QueryExpression.UnaryOperator.PLUS, reference("amount"));

        // Act + Assert
        assertRejected(expression, schema);
    }

    private static Stream<ValidationCase> nestedOperatorCases() {
        return Stream.of(
            new ValidationCase(
                "arithmetic",
                "amount",
                Integer.class,
                Set.of(QueryOperator.ADD),
                Set.of(QueryOperator.ADD, QueryOperator.GT),
                new QueryExpression.Binary(
                    QueryExpression.BinaryOperator.GREATER,
                    new QueryExpression.Binary(
                        QueryExpression.BinaryOperator.ADD,
                        reference("amount"),
                        new QueryExpression.Literal(0)
                    ),
                    new QueryExpression.Literal(18)
                )
            ),
            new ValidationCase(
                "length",
                "values",
                List.class,
                Set.of(QueryOperator.LENGTH),
                Set.of(QueryOperator.LENGTH, QueryOperator.GT),
                new QueryExpression.Binary(
                    QueryExpression.BinaryOperator.GREATER,
                    new QueryExpression.Length(reference("values")),
                    new QueryExpression.Literal(0)
                )
            ),
            new ValidationCase(
                "membership",
                "values",
                List.class,
                Set.of(QueryOperator.IN),
                Set.of(QueryOperator.IN, QueryOperator.EQ),
                new QueryExpression.Binary(
                    QueryExpression.BinaryOperator.EQUAL,
                    new QueryExpression.Binary(
                        QueryExpression.BinaryOperator.IN,
                        new QueryExpression.Literal(1),
                        reference("values")
                    ),
                    new QueryExpression.Literal(true)
                )
            ),
            new ValidationCase(
                "quantifier",
                "values",
                List.class,
                Set.of(QueryOperator.ANY),
                Set.of(QueryOperator.ANY, QueryOperator.EQ),
                new QueryExpression.Binary(
                    QueryExpression.BinaryOperator.EQUAL,
                    new QueryExpression.Quantifier(
                        QueryExpression.QuantifierOperator.ANY,
                        reference("values"),
                        "item",
                        new QueryExpression.Binary(
                            QueryExpression.BinaryOperator.GREATER,
                            new QueryExpression.Reference("item", List.of()),
                            new QueryExpression.Literal(0)
                        )
                    ),
                    new QueryExpression.Literal(true)
                )
            )
        );
    }

    private static void assertRejected(QueryExpression expression, QuerySchema<TestQuery> schema) {
        assertThatThrownBy(() -> QuerySchemaValidator.validate(expression, schema))
            .isInstanceOf(FilterByException.class)
            .hasMessageContaining("operator is not supported for query path");
    }

    private static QueryExpression.Reference reference(String path) {
        return new QueryExpression.Reference("object", List.of(path));
    }

    private static QuerySchema<TestQuery> schema(String path, Class<?> type, Set<QueryOperator> operators) {
        QueryField field = new QueryField(QueryPath.of(path), TypeDescriptor.of(type), false, operators);
        return new QuerySchema<>() {
            @Override
            public Class<TestQuery> queryType() {
                return TestQuery.class;
            }

            @Override
            public Optional<QueryField> field(QueryPath queryPath) {
                return field.path().equals(queryPath) ? Optional.of(field) : Optional.empty();
            }

            @Override
            public Collection<QueryField> fields() {
                return List.of(field);
            }
        };
    }

    private record ValidationCase(
        String name,
        String path,
        Class<?> type,
        Set<QueryOperator> innerOperators,
        Set<QueryOperator> allOperators,
        QueryExpression expression
    ) {
        @Override
        public String toString() {
            return this.name;
        }
    }

    private static final class TestQuery {}
}
