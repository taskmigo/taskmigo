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
import org.junit.jupiter.api.Test;

class AuthorizationCompilerTest {

    private final AuthorizationCompiler compiler = new AuthorizationCompiler(List.of(new AcceptingObjectQueryDialect()));

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
        assertThatThrownBy(() -> this.compiler.compile(statement(Target.REQUEST, Effect.ALLOW, "object.id != null"), Origin.CUSTOM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("object.* references are only valid");
    }

    @Test
    void rejectsUnsafeSpelConstructs() {
        assertThatThrownBy(() -> this.compiler.compile(statement(Target.OBJECT, Effect.ALLOW, "T(java.lang.Runtime).getRuntime() != null"), Origin.CUSTOM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported SpEL construct");

        assertThatThrownBy(() -> this.compiler.compile(statement(Target.OBJECT, Effect.ALLOW, "object.name.toString() == 'x'"), Origin.CUSTOM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only direct authorization property references are supported");
    }

    @Test
    void rejectsOrderedComparisonAgainstNull() {
        assertThatThrownBy(() -> this.compiler.compile(statement(Target.OBJECT, Effect.ALLOW, "object.archivedAt > null"), Origin.CUSTOM))
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
