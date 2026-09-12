package io.taskmigo.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationField;
import io.taskmigo.authorization.object.ObjectAuthorizationPath;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.ObjectAuthorizationSchemaRegistration;
import io.taskmigo.authorization.object.ObjectAuthorizationSchemaRegistry;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.language.EmbeddedLanguageCompiler;
import io.taskmigo.language.EmbeddedLanguageEvaluator;
import io.taskmigo.language.EmbeddedLanguageException;
import io.taskmigo.language.EmbeddedLanguagePartialEvaluator;
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
        List.of(this.schema),
        ObjectAuthorizationSchemaRegistry.all(List.of(this.schema))
    );
    private final ObjectAuthorizationService service = new ObjectAuthorizationService(
        new EmbeddedLanguagePartialEvaluator(),
        new EmbeddedLanguageCompiler(),
        ObjectAuthorizationSchemaRegistry.all(List.of(this.schema))
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

    /**
     * Verifies that a valid Object policy with a non-Boolean result is accepted during activation.
     *
     * Given: a syntactically valid Object policy returning a String for a registered route.
     * Expect: activation validation succeeds because Boolean enforcement belongs to evaluation.
     */
    @Test
    @DisplayName("accepts a non-boolean object policy during activation")
    void shouldAcceptNonBooleanObjectPolicyWhenActivationInputsAreValid() {
        // Arrange
        String policy = "return \"not-a-decision-yet\";";

        // Act + Assert
        assertThatCode(() -> this.service.validatePolicy(policy, "GET", "/api/v0/objects")).doesNotThrowAnyException();
    }

    /**
     * Verifies that a concrete non-Boolean Object result fails closed at authorization time.
     *
     * Given: an Object policy returning a Number and no symbolic Object input.
     * Expect: Object Authorization raises the domain authorization failure instead of granting access.
     */
    @Test
    @DisplayName("fails closed for a concrete non-boolean object result")
    void shouldFailClosedWhenConcreteObjectPolicyResultIsNotBoolean() {
        // Arrange
        AuthorizationOperation operation = operation(statement(Effect.ALLOW, "return 42;"));

        // Act + Assert
        assertThatThrownBy(() -> this.service.authorize(operation, this.schema))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("not Bool");
    }

    /**
     * Verifies that a residual non-Boolean Object result fails closed after partial evaluation.
     *
     * Given: an Object policy returning a symbolic String field and an empty known-input map.
     * Expect: the residual result is rejected because Object Authorization requires Bool at runtime.
     */
    @Test
    @DisplayName("fails closed for a residual non-boolean object result")
    void shouldFailClosedWhenResidualObjectPolicyResultIsNotBoolean() {
        // Arrange
        AuthorizationOperation operation = operation(statement(Effect.ALLOW, "return object.name;"));

        // Act + Assert
        assertThatThrownBy(() -> this.service.authorize(operation, this.schema))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("not Bool");
    }

    /**
     * Verifies that Object policy activation validates only schemas whose registered routes match the target.
     *
     * Given: two routes with different fields and a policy valid only for the first route.
     * Expect: the first target succeeds and the unrelated second target rejects the policy.
     */
    @Test
    @DisplayName("validates an object policy against only applicable schemas")
    void shouldValidateObjectPolicyAgainstApplicableSchemasOnly() {
        // Arrange
        ObjectAuthorizationSchema<TestObject> otherSchema = schema("other");
        ObjectAuthorizationService targetedService = new ObjectAuthorizationService(
            new EmbeddedLanguagePartialEvaluator(),
            new EmbeddedLanguageCompiler(),
            ObjectAuthorizationSchemaRegistry.of(
                List.of(
                    new ObjectAuthorizationSchemaRegistration("GET", "/api/v0/objects", this.schema),
                    new ObjectAuthorizationSchemaRegistration("GET", "/api/v0/other", otherSchema)
                )
            )
        );
        String policy = "return object.name == \"alice\";";

        // Act + Assert
        assertThatCode(() ->
            targetedService.validatePolicy(policy, "GET", "/api/v0/objects")
        ).doesNotThrowAnyException();
        assertThatThrownBy(() -> targetedService.validatePolicy(policy, "GET", "/api/v0/other")).isInstanceOf(
            EmbeddedLanguageException.class
        );
    }

    /**
     * Verifies that the typed Request result context can be reused by Object Authorization for the same operation.
     *
     * Given: one request allow Statement and one Object allow Statement resolved for a typed request.
     * Expect: Object Authorization accepts the returned opaque context and effective state resolves once.
     */
    @Test
    @DisplayName("reuses the typed request context for object authorization")
    void shouldReuseTypedRequestContextWhenObjectAuthorizationUsesSameOperation() {
        // Arrange
        EffectiveStatementResolver resolver = org.mockito.Mockito.mock(EffectiveStatementResolver.class);
        UUID userId = UUID.randomUUID();
        StatementInfo requestStatement = new StatementInfo(
            UUID.randomUUID(),
            "request_statement",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", "/api/v0/objects")),
            "return true;"
        );
        StatementInfo objectStatement = statement(Effect.ALLOW, "return object.name == \"alice\";");
        org.mockito.Mockito.when(resolver.resolve(userId)).thenReturn(List.of(requestStatement, objectStatement));
        RequestAuthorizationService requestAuthorization = new RequestAuthorizationService(
            resolver,
            new EmbeddedLanguageEvaluator(),
            this.artifacts
        );

        // Act
        RequestAuthorizationResult result = requestAuthorization.authorize(
            new AuthorizationPrincipal(userId, "alice"),
            new AuthorizationRequest("GET", "/api/v0/objects", Map.of())
        );
        ObjectAuthorizationPredicate<TestObject> predicate = this.service.authorize(result.context(), this.schema);

        // Assert
        assertThat(result.granted()).isTrue();
        assertThat(predicate.isAlwaysTrue()).isFalse();
        assertThat(predicate.isAlwaysFalse()).isFalse();
        org.mockito.Mockito.verify(resolver).resolve(userId);
    }

    /**
     * Verifies that Object Authorization uses the public composed Statement target path.
     *
     * Given: a schema exposing `target.api.path` and policies using either the public or persistence-shaped name.
     * Expect: the public path is accepted and the persistence-shaped path is rejected.
     */
    @Test
    @DisplayName("uses API-visible nested paths for object authorization")
    void shouldUseApiVisibleStatementPathWhenObjectPolicyReferencesTarget() {
        // Arrange
        ObjectAuthorizationSchema<TestObject> apiSchema = schema("target.api.path");
        ObjectAuthorizationService apiService = new ObjectAuthorizationService(
            new EmbeddedLanguagePartialEvaluator(),
            new EmbeddedLanguageCompiler(),
            ObjectAuthorizationSchemaRegistry.all(List.of(apiSchema))
        );

        // Act + Assert
        assertThatCode(() ->
            apiService.validatePolicy("return object.target.api.path == \"/api/v0/users\";", "GET", "/api/v0/users")
        ).doesNotThrowAnyException();
        assertThatThrownBy(() ->
            apiService.validatePolicy("return object.path == \"/api/v0/users\";", "GET", "/api/v0/users")
        ).isInstanceOf(EmbeddedLanguageException.class);
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
        return schema("name");
    }

    private static ObjectAuthorizationSchema<TestObject> schema(String path) {
        ObjectAuthorizationField field = new ObjectAuthorizationField(
            ObjectAuthorizationPath.parse(path),
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
