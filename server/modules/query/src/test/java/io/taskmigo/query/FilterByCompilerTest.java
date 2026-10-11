package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.foundation.TypeDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FilterByCompilerTest {

    private final QuerySchemaView schema = schema(
        "test.customers.list",
        List.of(
            new QueryFieldDescriptor(
                QueryPath.of("name"),
                TypeDescriptor.of(String.class),
                false,
                Set.of(QueryOperator.EQ)
            )
        )
    );

    /**
     * Verifies that a missing client filter is represented by the logical identity predicate.
     *
     * Given: a Query Schema and a null filterBy value.
     * Expect: compilation returns a predicate that is always true.
     */
    @Test
    @DisplayName("should return an always-true predicate when filterBy is absent")
    void shouldReturnAlwaysTrueWhenFilterByIsAbsent() {
        // Arrange
        FilterByCompiler compiler = new FilterByCompiler();

        // Act
        QueryPredicate<?> result = compiler.compile(this.schema, null);

        // Assert
        assertThat(result.isAlwaysTrue()).isTrue();
    }

    /**
     * Verifies that a registered API path compiles as a Boolean logical predicate.
     *
     * Given: the registered path `name` and a valid equality expression.
     * Expect: compilation succeeds and the result is not an unconditional predicate.
     */
    @Test
    @DisplayName("should compile a registered path when filterBy is boolean")
    void shouldCompileRegisteredPathWhenFilterByIsBoolean() {
        // Arrange
        FilterByCompiler compiler = new FilterByCompiler();

        // Act
        QueryPredicate<?> result = compiler.compile(this.schema, "object.name == \"Phong\"");

        // Assert
        assertThat(result.isAlwaysTrue()).isFalse();
        assertThat(result.isAlwaysFalse()).isFalse();
    }

    /**
     * Verifies that persistence-only names cannot enter the logical query surface.
     *
     * Given: a filter referencing `object.email` while only `name` is registered.
     * Expect: the compiler reports invalid filter input.
     */
    @Test
    @DisplayName("should reject an unregistered path when filterBy references persistence data")
    void shouldRejectUnregisteredPathWhenFilterByReferencesPersistenceData() {
        // Arrange
        FilterByCompiler compiler = new FilterByCompiler();

        // Act + Assert
        assertThatThrownBy(() -> compiler.compile(this.schema, "object.email == \"a@example.com\"")).isInstanceOf(
            FilterByException.class
        );
    }

    /**
     * Verifies that logical operators compose fields without requiring a logical operator on each scalar field.
     *
     * Given: two registered scalar fields joined by `&&`.
     * Expect: compilation succeeds as a non-constant Boolean Query Predicate.
     */
    @Test
    @DisplayName("should compile a compound filter when scalar fields are combined")
    void shouldCompileCompoundFilterWhenScalarFieldsAreCombined() {
        // Arrange
        QuerySchemaView compoundSchema = schema(
            "test.customers.compound",
            List.of(
                new QueryFieldDescriptor(
                    QueryPath.of("name"),
                    TypeDescriptor.of(String.class),
                    false,
                    Set.of(QueryOperator.EQ)
                ),
                new QueryFieldDescriptor(
                    QueryPath.of("email"),
                    TypeDescriptor.of(String.class),
                    false,
                    Set.of(QueryOperator.EQ)
                )
            )
        );

        // Act
        QueryPredicate<?> result = new FilterByCompiler().compile(
            compoundSchema,
            "object.name == \"Phong\" && object.email == \"p@example.com\""
        );

        // Assert
        assertThat(result.isAlwaysTrue()).isFalse();
        assertThat(result.isAlwaysFalse()).isFalse();
    }

    /**
     * Verifies that nested Statement fields are addressed by their public API paths.
     *
     * Given: a schema exposing `target.api.method` and `target.api.path`.
     * Expect: the composed path compiles while persistence-shaped `object.method` is rejected.
     */
    @Test
    @DisplayName("should use API-visible nested paths for Statement filtering")
    void shouldCompileApiVisibleStatementPathWhenFilterReferencesTarget() {
        // Arrange
        QuerySchemaView statementSchema = schema(
            "test.statements.list",
            List.of(
                new QueryFieldDescriptor(
                    QueryPath.parse("target.api.method"),
                    TypeDescriptor.of(String.class),
                    false,
                    Set.of(QueryOperator.EQ)
                ),
                new QueryFieldDescriptor(
                    QueryPath.parse("target.api.path"),
                    TypeDescriptor.of(String.class),
                    false,
                    Set.of(QueryOperator.EQ)
                )
            )
        );

        // Act
        QueryPredicate<?> result = new FilterByCompiler().compile(
            statementSchema,
            "object.target.api.method == \"GET\""
        );

        // Assert
        assertThat(result.isAlwaysTrue()).isFalse();
        assertThatThrownBy(() ->
            new FilterByCompiler().compile(statementSchema, "object.method == \"GET\"")
        ).isInstanceOf(FilterByException.class);
    }

    /**
     * Verifies that operation-schema identity changes when the exposed field contract changes.
     *
     * Given: two schemas with the same operation name but different nullability contracts.
     * Expect: compiled predicates carry distinct schema identities.
     */
    @Test
    @DisplayName("should produce different predicate identities when schema contracts differ")
    void shouldProduceDifferentPredicateIdentitiesWhenSchemaContractsDiffer() {
        // Arrange
        QueryPredicate<?> left = new FilterByCompiler().compile(this.schema, "object.name == \"Phong\"");
        QuerySchemaView incompatible = schema(
            "test.customers.list",
            List.of(
                new QueryFieldDescriptor(
                    QueryPath.of("name"),
                    TypeDescriptor.of(String.class),
                    true,
                    Set.of(QueryOperator.EQ)
                )
            )
        );
        QueryPredicate<?> right = new FilterByCompiler().compile(incompatible, "object.name == \"Phong\"");

        // Act
        String leftIdentity = QueryPredicateFactory.schemaIdentity(left);
        String rightIdentity = QueryPredicateFactory.schemaIdentity(right);

        // Assert
        assertThat(leftIdentity).isNotEqualTo(rightIdentity);
    }

    private static QuerySchemaView schema(String operation, Collection<QueryFieldDescriptor> fields) {
        List<QueryFieldDescriptor> declared = List.copyOf(fields);
        return new QuerySchemaView() {
            @Override
            public String operation() {
                return operation;
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return declared;
            }
        };
    }
}
