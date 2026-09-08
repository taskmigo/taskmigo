package io.taskmigo.auth.authorization.object;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.auth.authorization.request.AuthorizationOperation;
import io.taskmigo.auth.authorization.request.AuthorizationSnapshot;
import io.taskmigo.auth.authorization.request.StatementArtifactFactory;
import io.taskmigo.auth.authorization.statement.ApiInfo;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.TargetInfo;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageCompiler;
import io.taskmigo.embeddedlanguage.EmbeddedLanguagePartialEvaluator;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

class ObjectAuthorizationServiceTest {
    private final ObjectAuthorizationSchema<TestObject> schema = schema();
    private final StatementArtifactFactory artifacts = new StatementArtifactFactory(
        new EmbeddedLanguageCompiler(),
        List.of(this.schema)
    );
    private final ObjectAuthorizationService service = new ObjectAuthorizationService(
        new EmbeddedLanguagePartialEvaluator(),
        new EmbeddedLanguageCompiler(),
        List.of(this.schema)
    );

    /**
     * Verifies that an unconditional allow policy becomes an opaque predicate granting every object.
     *
     * Given: one matching Object Authorization Statement with an unconditional allow policy.
     * Expect: the returned predicate is always true and exposes no persistence representation.
     */
    @Test
    @DisplayName("returns an always true predicate for an unconditional allow")
    void shouldReturnAlwaysTrueWhenAllowPolicyIsUnconditional() {
        // Arrange
        AuthorizationOperation operation = operation(statement(Effect.ALLOW, "return true;"));

        // Act
        ObjectAuthorizationPredicate<TestObject> predicate = this.service.authorize(operation, this.schema);

        // Assert
        assertThat(predicate.isAlwaysTrue()).isTrue();
        assertThat(predicate.isAlwaysFalse()).isFalse();
    }

    /**
     * Verifies that an unconditional deny policy produces a no-row authorization predicate.
     *
     * Given: one matching Object Authorization Statement with an unconditional deny policy.
     * Expect: the returned predicate is always false before any resource query is paginated.
     */
    @Test
    @DisplayName("returns an always false predicate for an unconditional deny")
    void shouldReturnAlwaysFalseWhenDenyPolicyIsUnconditional() {
        // Arrange
        AuthorizationOperation operation = operation(statement(Effect.DENY, "return true;"));

        // Act
        ObjectAuthorizationPredicate<TestObject> predicate = this.service.authorize(operation, this.schema);

        // Assert
        assertThat(predicate.isAlwaysFalse()).isTrue();
        assertThat(predicate.isAlwaysTrue()).isFalse();
    }

    /**
     * Verifies that an object-dependent policy remains opaque instead of being evaluated over JVM rows.
     *
     * Given: a matching allow policy that compares a persisted object field with a literal.
     * Expect: the predicate is neither constant, so the resource binder must bind it to persistence.
     */
    @Test
    @DisplayName("keeps an object dependent policy as an opaque predicate")
    void shouldKeepPredicateOpaqueWhenPolicyReferencesObject() {
        // Arrange
        AuthorizationOperation operation = operation(statement(Effect.ALLOW, "return object.name == \"alice\";"));

        // Act
        ObjectAuthorizationPredicate<TestObject> predicate = this.service.authorize(operation, this.schema);

        // Assert
        assertThat(predicate.isAlwaysTrue()).isFalse();
        assertThat(predicate.isAlwaysFalse()).isFalse();
    }

    private AuthorizationOperation operation(StatementInfo statement) {
        List<StatementExecutionArtifact> executable = this.artifacts.build(List.of(statement));
        return new AuthorizationOperation(
            new AuthorizationSnapshot(UUID.randomUUID(), List.of(statement), executable, Map.of()),
            "GET",
            "/api/v0/objects"
        );
    }

    private static StatementInfo statement(Effect effect, String policy) {
        return new StatementInfo(
            UUID.randomUUID(),
            "object_statement",
            null,
            effect,
            Scope.OBJECT,
            new TargetInfo(new ApiInfo("GET", "/api/v0/objects")),
            policy
        );
    }

    private static ObjectAuthorizationSchema<TestObject> schema() {
        ObjectAuthorizationField field = new ObjectAuthorizationField(
            ObjectAuthorizationPath.of("name"),
            ResolvableType.forClass(String.class),
            false
        );
        return new ObjectAuthorizationSchema<>() {
            @Override
            public Class<TestObject> objectType() {
                return TestObject.class;
            }

            @Override
            public Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath path) {
                return field.path().equals(path) ? Optional.of(field) : Optional.empty();
            }

            @Override
            public Collection<ObjectAuthorizationField> fields() {
                return List.of(field);
            }
        };
    }

    private static final class TestObject {}
}
