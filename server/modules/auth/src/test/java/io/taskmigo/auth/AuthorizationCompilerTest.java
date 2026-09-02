package io.taskmigo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthorizationCompilerTest {

    private final AuthorizationCompiler compiler = new AuthorizationCompiler();

    /**
     * Verifies that operators are compiled according to the DSL precedence rules.
     *
     * Given: a condition combining arithmetic, comparison, AND, and OR operators.
     * Expect: the AST places multiplication below comparison and AND below OR in the correct order.
     */
    @Test
    @DisplayName("compiles operators with the required precedence")
    void shouldCompileOperatorsWhenExpressionUsesMultiplePrecedenceLevels() {
        // Arrange
        StatementService.StatementInfo statement = statement(
            "request.count > 1 + 2 * 3 && request.active == true || request.path == '/users'"
        );

        // Act
        AuthorizationCompiler.Expression result = this.compiler.compile(statement);

        // Assert
        assertThat(result).isInstanceOf(AuthorizationCompiler.Binary.class);
        AuthorizationCompiler.Binary or = (AuthorizationCompiler.Binary) result;
        assertThat(or.operator()).isEqualTo(AuthorizationCompiler.BinaryOperator.OR);
        assertThat(or.left()).isInstanceOf(AuthorizationCompiler.Binary.class);
        AuthorizationCompiler.Binary and = (AuthorizationCompiler.Binary) or.left();
        assertThat(and.operator()).isEqualTo(AuthorizationCompiler.BinaryOperator.AND);
        assertThat(and.left()).isInstanceOf(AuthorizationCompiler.Binary.class);
        AuthorizationCompiler.Binary greater = (AuthorizationCompiler.Binary) and.left();
        assertThat(greater.operator()).isEqualTo(AuthorizationCompiler.BinaryOperator.GREATER);
        assertThat(greater.right()).isInstanceOf(AuthorizationCompiler.Binary.class);
        assertThat(((AuthorizationCompiler.Binary) greater.right()).operator()).isEqualTo(
            AuthorizationCompiler.BinaryOperator.ADD
        );
        assertThat(
            ((AuthorizationCompiler.Binary) ((AuthorizationCompiler.Binary) greater.right()).right()).operator()
        ).isEqualTo(AuthorizationCompiler.BinaryOperator.MULTIPLY);
    }

    /**
     * Verifies that all supported value roots compile into property references.
     *
     * Given: one condition referencing principal, request, and object values in an object Statement.
     * Expect: each reference preserves its root and dotted property path in the compiled AST.
     */
    @Test
    @DisplayName("compiles supported reference roots")
    void shouldCompileReferencesWhenExpressionUsesSupportedRoots() {
        // Arrange
        StatementService.StatementInfo statement = statement(
            "object.id == principal.id && request.path.organizationId == principal.organizationId",
            StatementService.TargetType.OBJECT
        );

        // Act
        AuthorizationCompiler.Expression result = this.compiler.compile(statement);

        // Assert
        assertThat(result).isInstanceOf(AuthorizationCompiler.Binary.class);
        AuthorizationCompiler.Binary conditions = (AuthorizationCompiler.Binary) result;
        assertThat(conditions.operator()).isEqualTo(AuthorizationCompiler.BinaryOperator.AND);
        assertThat(conditions.left()).isInstanceOf(AuthorizationCompiler.Binary.class);
        AuthorizationCompiler.Binary objectComparison = (AuthorizationCompiler.Binary) conditions.left();
        assertThat(((AuthorizationCompiler.Reference) objectComparison.left()).path()).containsExactly("id");
        AuthorizationCompiler.Binary requestComparison = (AuthorizationCompiler.Binary) conditions.right();
        assertThat(((AuthorizationCompiler.Reference) requestComparison.left()).path()).containsExactly(
            "path",
            "organizationId"
        );
    }

    /**
     * Verifies that an empty condition list represents an unconditional Statement.
     *
     * Given: a Statement with no conditions.
     * Expect: compilation returns a boolean true literal without requiring a placeholder expression.
     */
    @Test
    @DisplayName("compiles empty conditions as unconditional true")
    void shouldCompileUnconditionalExpressionWhenStatementHasNoConditions() {
        // Arrange
        StatementService.StatementInfo statement = statement();

        // Act
        AuthorizationCompiler.Expression result = this.compiler.compile(statement);

        // Assert
        assertThat(result).isEqualTo(new AuthorizationCompiler.LiteralValue(true));
    }

    /**
     * Verifies that object references are not accepted by request Statements.
     *
     * Given: a request Statement containing `object.id`.
     * Expect: compilation fails with an authorization validation error.
     */
    @Test
    @DisplayName("rejects object references in request statements")
    void shouldRejectObjectReferenceWhenStatementTargetsRequests() {
        // Arrange
        StatementService.StatementInfo statement = statement("object.id == principal.id");

        // Act + Assert
        assertThatThrownBy(() -> this.compiler.compile(statement))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("only valid for object Statements");
    }

    /**
     * Verifies that unsupported language capabilities are rejected instead of being interpreted dynamically.
     *
     * Given: a condition containing a method call.
     * Expect: compilation fails before producing an authorization AST.
     */
    @Test
    @DisplayName("rejects unsupported method calls")
    void shouldRejectMethodCallWhenExpressionUsesUnsupportedLanguageFeature() {
        // Arrange
        StatementService.StatementInfo statement = statement("request.path.startsWith('/api')");

        // Act + Assert
        assertThatThrownBy(() -> this.compiler.compile(statement))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("unsupported");
    }

    /**
     * Verifies that excessive nesting is rejected to keep compilation bounded.
     *
     * Given: an expression nested deeper than the compiler's configured depth limit.
     * Expect: compilation fails with a bounded-complexity validation error.
     */
    @Test
    @DisplayName("rejects expressions that exceed nesting bounds")
    void shouldRejectExpressionWhenNestingExceedsBound() {
        // Arrange
        String expression = "(".repeat(21) + "request.value" + ")".repeat(21);

        // Act + Assert
        assertThatThrownBy(() -> this.compiler.compile(statement(expression)))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("too deep");
    }

    private static StatementService.StatementInfo statement(String... conditions) {
        return statement(List.of(conditions), StatementService.TargetType.REQUEST);
    }

    private static StatementService.StatementInfo statement(String condition, StatementService.TargetType type) {
        return statement(List.of(condition), type);
    }

    private static StatementService.StatementInfo statement(List<String> conditions, StatementService.TargetType type) {
        return new StatementService.StatementInfo(
            UUID.randomUUID(),
            "test.statement",
            null,
            StatementService.Effect.ALLOW,
            new StatementService.TargetInfo(type, new StatementService.ApiInfo("GET", "/api/v0/test")),
            conditions
        );
    }
}
