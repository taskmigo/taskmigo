package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

class FilterByCompilerTest {

    private final QuerySchema<CustomerQuery> schema = new QuerySchema<>() {
        private final QueryField name = new QueryField(
            QueryPath.of("name"),
            ResolvableType.forClass(String.class),
            false,
            Set.of(QueryOperator.EQ)
        );

        @Override
        public Class<CustomerQuery> queryType() {
            return CustomerQuery.class;
        }

        @Override
        public Optional<QueryField> field(QueryPath path) {
            return this.fields()
                .stream()
                .filter(field -> field.path().equals(path))
                .findFirst();
        }

        @Override
        public Collection<QueryField> fields() {
            return List.of(this.name);
        }
    };

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
        QueryPredicate<CustomerQuery> result = compiler.compile(this.schema, null);

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
        QueryPredicate<CustomerQuery> result = compiler.compile(this.schema, "object.name == \"Phong\"");

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
        QuerySchema<CustomerQuery> compoundSchema = new QuerySchema<>() {
            @Override
            public Class<CustomerQuery> queryType() {
                return CustomerQuery.class;
            }

            @Override
            public Optional<QueryField> field(QueryPath path) {
                return this.fields()
                    .stream()
                    .filter(field -> field.path().equals(path))
                    .findFirst();
            }

            @Override
            public Collection<QueryField> fields() {
                return List.of(
                    new QueryField(
                        QueryPath.of("name"),
                        ResolvableType.forClass(String.class),
                        false,
                        Set.of(QueryOperator.EQ)
                    ),
                    new QueryField(
                        QueryPath.of("email"),
                        ResolvableType.forClass(String.class),
                        false,
                        Set.of(QueryOperator.EQ)
                    )
                );
            }
        };

        // Act
        QueryPredicate<CustomerQuery> result = new FilterByCompiler().compile(
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
        QuerySchema<StatementQuery> statementSchema = new QuerySchema<>() {
            private final List<QueryField> fields = List.of(
                new QueryField(QueryPath.parse("target.api.method"), ResolvableType.forClass(String.class), false),
                new QueryField(QueryPath.parse("target.api.path"), ResolvableType.forClass(String.class), false)
            );

            @Override
            public Class<StatementQuery> queryType() {
                return StatementQuery.class;
            }

            @Override
            public Optional<QueryField> field(QueryPath path) {
                return this.fields
                    .stream()
                    .filter(field -> field.path().equals(path))
                    .findFirst();
            }

            @Override
            public Collection<QueryField> fields() {
                return this.fields;
            }
        };

        // Act
        QueryPredicate<StatementQuery> result = new FilterByCompiler().compile(
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
     * Verifies that Query Predicate composition rejects predicates from different schema revisions.
     *
     * Given: one predicate bound to the current schema and one bound to a schema with a different field contract.
     * Expect: composition fails before a predicate can cross the schema boundary.
     */
    @Test
    @DisplayName("should reject predicate composition when schema identities differ")
    void shouldRejectPredicateCompositionWhenSchemaIdentitiesDiffer() {
        // Arrange
        QueryPredicate<CustomerQuery> left = new FilterByCompiler().compile(this.schema, "object.name == \"Phong\"");
        QuerySchema<CustomerQuery> incompatible = new QuerySchema<>() {
            @Override
            public Class<CustomerQuery> queryType() {
                return CustomerQuery.class;
            }

            @Override
            public Optional<QueryField> field(QueryPath path) {
                return Optional.of(
                    new QueryField(path, ResolvableType.forClass(String.class), true, Set.of(QueryOperator.EQ))
                );
            }

            @Override
            public Collection<QueryField> fields() {
                return List.of(
                    new QueryField(
                        QueryPath.of("name"),
                        ResolvableType.forClass(String.class),
                        true,
                        Set.of(QueryOperator.EQ)
                    )
                );
            }
        };
        QueryPredicate<CustomerQuery> right = new FilterByCompiler().compile(incompatible, "object.name == \"Phong\"");

        // Act + Assert
        assertThatThrownBy(() -> QueryPredicates.standard().and(left, right))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("incompatible");
    }

    private static final class CustomerQuery {}

    private static final class StatementQuery {}
}
