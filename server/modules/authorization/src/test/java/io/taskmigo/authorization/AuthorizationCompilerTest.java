package io.taskmigo.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.AuthorizationResource.Effect;
import io.taskmigo.authorization.AuthorizationResource.Match;
import io.taskmigo.authorization.AuthorizationResource.Origin;
import io.taskmigo.authorization.AuthorizationResource.Statement;
import io.taskmigo.authorization.AuthorizationResource.Target;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AuthorizationCompilerTest {

    private final AuthorizationCompiler compiler = new AuthorizationCompiler(
        List.of(new AcceptingObjectQueryDialect())
    );

    @Test
    void compilesAndCachesSafeObjectCondition() {
        UUID id = UUID.randomUUID();
        Statement statement = statement(
            Target.OBJECT,
            Effect.ALLOW,
            "object.organizationId == principal.organizationId"
        );

        CompiledStatement first = this.compiler.compileCached(id, statement, Origin.CUSTOM);
        CompiledStatement second = this.compiler.compileCached(id, statement, Origin.CUSTOM);

        assertThat(second).isSameAs(first);
        assertThat(first.path().matches("/api/v0/projects")).isTrue();
        assertThat(first.path().matches("/api/v0/projects/123")).isFalse();
    }

    @Test
    void replacesCachedStatementWhenResourceChanges() {
        UUID id = UUID.randomUUID();
        Statement before = statement(Target.REQUEST, Effect.ALLOW, "principal.id != null");
        Statement after = new Statement(
            before.key(),
            before.name(),
            before.description(),
            new Match("POST", "/api/v0/projects"),
            Target.REQUEST,
            Effect.DENY,
            "principal.id != null",
            List.of()
        );

        CompiledStatement first = this.compiler.compileCached(id, before, Origin.CUSTOM);
        CompiledStatement second = this.compiler.compileCached(id, after, Origin.CUSTOM);

        assertThat(second).isNotSameAs(first);
        assertThat(second.method()).isEqualTo("POST");
        assertThat(second.effect()).isEqualTo(Effect.DENY);
    }

    @Test
    void rejectsObjectStatementWithoutDatabaseDialect() {
        AuthorizationCompiler withoutDialect = new AuthorizationCompiler(List.of());

        assertThatThrownBy(() ->
            withoutDialect.compile(statement(Target.OBJECT, Effect.ALLOW, "object.id != null"), Origin.CUSTOM)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No database authorization query dialect supports");
    }

    @Test
    void rejectsObjectReferenceFromRequestStatement() {
        assertThatThrownBy(() ->
            this.compiler.compile(statement(Target.REQUEST, Effect.ALLOW, "object.id != null"), Origin.CUSTOM)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("object.* references are only valid");
    }

    @ParameterizedTest
    @MethodSource("forbiddenExpressions")
    void rejectsUnsafeSpelConstructs(String expression) {
        assertThatThrownBy(() ->
            this.compiler.compile(statement(Target.OBJECT, Effect.ALLOW, expression), Origin.CUSTOM)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported SpEL construct");
    }

    @Test
    void rejectsOrderedComparisonAgainstNull() {
        assertThatThrownBy(() ->
            this.compiler.compile(statement(Target.OBJECT, Effect.ALLOW, "object.archivedAt > null"), Origin.CUSTOM)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ordered comparison against null");
    }

    @Test
    void rejectsNonCanonicalMethodAndInvalidRegex() {
        Statement lowercase = new Statement(
            "project.read",
            null,
            null,
            new Match("get", "/api/v0/projects"),
            Target.REQUEST,
            Effect.ALLOW,
            null,
            List.of()
        );
        Statement invalidRegex = new Statement(
            "project.read",
            null,
            null,
            new Match("GET", "/api/v0/projects(["),
            Target.REQUEST,
            Effect.ALLOW,
            null,
            List.of()
        );

        assertThatThrownBy(() -> this.compiler.compile(lowercase, Origin.CUSTOM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("canonical uppercase");
        assertThatThrownBy(() -> this.compiler.compile(invalidRegex, Origin.CUSTOM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or unsupported match.path regex");
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsMissingRequiredFields() {
        Statement missingMatch = new Statement(
            "project.read",
            null,
            null,
            null,
            Target.REQUEST,
            Effect.ALLOW,
            null,
            List.of()
        );
        Statement missingTarget = new Statement(
            "project.read",
            null,
            null,
            new Match("GET", "/api/v0/projects"),
            null,
            Effect.ALLOW,
            null,
            List.of()
        );

        assertThatThrownBy(() -> this.compiler.compile(missingMatch, Origin.CUSTOM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("match is required");
        assertThatThrownBy(() -> this.compiler.compile(missingTarget, Origin.CUSTOM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("target is required");
    }

    @Test
    void rejectsExpressionBeyondComplexityLimit() {
        String expression = "!(".repeat(33) + "true" + ")".repeat(33);

        assertThatThrownBy(() ->
            this.compiler.compile(statement(Target.REQUEST, Effect.ALLOW, expression), Origin.CUSTOM)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("AST complexity limits");
    }

    private static Stream<String> forbiddenExpressions() {
        return Stream.of(
            "T(java.lang.Runtime).getRuntime() != null",
            "object.name.toString() == 'x'",
            "new java.lang.String('x') == 'x'",
            "@environment != null",
            "#unknown()",
            "object.name = 'x'",
            "object.items.![name] != null",
            "object.items.?[true] != null"
        );
    }

    private static Statement statement(Target target, Effect effect, String condition) {
        return new Statement(
            "project.read",
            null,
            null,
            new Match("GET", "/api/v0/projects"),
            target,
            effect,
            condition,
            List.of()
        );
    }

    private static final class AcceptingObjectQueryDialect implements AuthorizationObjectQueryDialect {

        @Override
        public String method() {
            return "GET";
        }

        @Override
        public String path() {
            return "/api/v0/projects";
        }

        @Override
        public void validate(AuthorizationExpression expression) {}
    }
}
