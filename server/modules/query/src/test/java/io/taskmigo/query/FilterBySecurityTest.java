package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.foundation.TypeDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FilterBySecurityTest {

    private final QuerySchemaView schema = new QuerySchemaView() {
        @Override
        public String operation() {
            return "test.users.list";
        }

        @Override
        public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
            return List.of(
                new QueryFieldDescriptor(
                    QueryPath.of("username"),
                    TypeDescriptor.of(String.class),
                    false,
                    Set.of(QueryOperator.EQ)
                )
            );
        }
    };

    /**
     * Given: a client filter containing a parenthesized OR tautology over one allowlisted object field.
     * Expect: filter compilation succeeds as an independent client predicate; authorization is composed elsewhere.
     */
    @Test
    @DisplayName("compiles parenthesized client OR independently from authorization")
    void shouldCompileClientOrWhenExpressionUsesOnlyAllowedObjectRoot() {
        // Arrange
        FilterByCompiler compiler = new FilterByCompiler();

        // Act
        QueryPredicate<?> predicate = compiler.compile(this.schema, "(object.username == \"alice\" || true)");

        // Assert
        assertThat(predicate).isNotNull();
    }

    /**
     * Given: a client filter attempting to use unary NOT, which is outside the approved JPA operator set.
     * Expect: compilation rejects the expression before JPA binding.
     */
    @Test
    @DisplayName("rejects unary NOT in client filter")
    void shouldRejectClientFilterWhenExpressionUsesUnaryNot() {
        // Arrange
        FilterByCompiler compiler = new FilterByCompiler();

        // Act + Assert
        assertThatThrownBy(() -> compiler.compile(this.schema, "!(object.username == \"alice\")")).isInstanceOf(
            FilterByException.class
        );
    }

    /**
     * Given: a client filter attempting to read the trusted principal root.
     * Expect: compilation rejects the expression instead of exposing authorization context.
     */
    @Test
    @DisplayName("rejects principal root in client filter")
    void shouldRejectClientFilterWhenExpressionReferencesPrincipal() {
        // Arrange
        FilterByCompiler compiler = new FilterByCompiler();

        // Act + Assert
        assertThatThrownBy(() -> compiler.compile(this.schema, "principal.username == object.username")).isInstanceOf(
            FilterByException.class
        );
    }

    /**
     * Given: a syntactically incomplete filter expression from an untrusted client.
     * Expect: compilation rejects the malformed input rather than treating it as an absent filter.
     */
    @Test
    @DisplayName("rejects malformed client filter")
    void shouldRejectClientFilterWhenExpressionIsMalformed() {
        // Arrange
        FilterByCompiler compiler = new FilterByCompiler();

        // Act + Assert
        assertThatThrownBy(() -> compiler.compile(this.schema, "object.username ==")).isInstanceOf(
            FilterByException.class
        );
    }
}
