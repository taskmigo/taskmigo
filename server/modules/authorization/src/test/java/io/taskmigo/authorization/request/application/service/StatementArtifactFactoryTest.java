package io.taskmigo.authorization.request.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationCompilationProfile;
import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationFieldBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationOperator;
import io.taskmigo.authorization.object.StaticObjectAuthorizationBinding;
import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.request.application.port.out.EffectiveStatement;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.language.CompilationMode;
import io.taskmigo.language.Field;
import io.taskmigo.language.FieldId;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageContract;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementArtifactFactoryTest {

    private final StatementArtifactFactory factory = new StatementArtifactFactory(
        new LanguageCompiler(),
        List.of(),
        ObjectAuthorizationTargetResolver.all(List.of())
    );

    /**
     * Verifies object policies compile independently for resources that expose the same path.
     *
     * Given: two target bindings with `object.name` but distinct semantic field identities.
     * Expect: one artifact contains a variant for each resource instead of constructing an ambiguous union schema.
     */
    @Test
    @DisplayName("compiles an object policy separately for equal paths with distinct identities")
    void shouldCompileSeparateVariantsWhenObjectBindingsSharePathWithDifferentIds() {
        // Arrange
        ObjectAuthorizationBinding<FirstObject> first = binding(
            FirstObject.class,
            "test:first-object",
            "field:test:first-object:name"
        );
        ObjectAuthorizationBinding<SecondObject> second = binding(
            SecondObject.class,
            "test:second-object",
            "field:test:second-object:name"
        );
        StatementArtifactFactory objectFactory = new StatementArtifactFactory(
            new LanguageCompiler(),
            List.of(first, second),
            ObjectAuthorizationTargetResolver.all(List.of(first, second))
        );
        EffectiveStatement statement = effective(
            new StatementInfo(
                UUID.randomUUID(),
                "shared_path",
                null,
                Effect.ALLOW,
                Scope.OBJECT,
                new TargetInfo(new ApiInfo("GET", "/api/v0/objects")),
                "object.name == \"alice\""
            ),
            Instant.EPOCH
        );

        // Act
        StatementExecutionArtifact artifact = objectFactory
            .build(List.of(statement), "GET", "/api/v0/objects")
            .getFirst();

        // Assert
        assertThat(artifact.policy(first.resourceType(), first.schemaFingerprint())).isNotNull();
        assertThat(artifact.policy(second.resourceType(), second.schemaFingerprint())).isNotNull();
    }

    /**
     * Verifies one logical resource can retain policy variants for multiple effective schema fingerprints.
     *
     * Given: one Statement target resolves two snapshots of the same resource type with different nullability.
     * Expect: the artifact keeps both exact resource-type/fingerprint variants without treating them as a conflict.
     */
    @Test
    @DisplayName("compiles object policy variants for multiple fingerprints of one resource")
    void shouldCompileSeparateVariantsWhenSameResourceHasDifferentFingerprints() {
        // Arrange
        ResourceType type = ResourceType.of("test:runtime-object");
        FieldId id = FieldId.of("field:test:runtime-object:name");
        ResourceSchema requiredSchema = ResourceSchema.of(
            type,
            List.of(new Field(id, FieldPath.parse("name"), LanguageType.Scalar.STRING, false))
        );
        ResourceSchema nullableSchema = ResourceSchema.of(
            type,
            List.of(new Field(id, FieldPath.parse("name"), LanguageType.Scalar.STRING, true))
        );
        ObjectAuthorizationBinding<FirstObject> required = new StaticObjectAuthorizationBinding<>(
            FirstObject.class,
            requiredSchema,
            List.of(new ObjectAuthorizationFieldBinding(id, "name", String.class, Set.of(ObjectAuthorizationOperator.EQ)))
        );
        ObjectAuthorizationBinding<SecondObject> nullable = new StaticObjectAuthorizationBinding<>(
            SecondObject.class,
            nullableSchema,
            List.of(new ObjectAuthorizationFieldBinding(id, "name", String.class, Set.of(ObjectAuthorizationOperator.EQ)))
        );
        StatementArtifactFactory objectFactory = new StatementArtifactFactory(
            new LanguageCompiler(),
            List.of(required, nullable),
            ObjectAuthorizationTargetResolver.all(List.of(required, nullable))
        );
        EffectiveStatement statement = effective(
            new StatementInfo(
                UUID.randomUUID(),
                "runtime_schema",
                null,
                Effect.ALLOW,
                Scope.OBJECT,
                new TargetInfo(new ApiInfo("GET", "/api/v0/objects")),
                "object.name == \"alice\""
            ),
            Instant.EPOCH
        );

        // Act
        StatementExecutionArtifact artifact = objectFactory
            .build(List.of(statement), "GET", "/api/v0/objects")
            .getFirst();

        // Assert
        assertThat(artifact.policy(type, required.schemaFingerprint())).isNotNull();
        assertThat(artifact.policy(type, nullable.schemaFingerprint())).isNotNull();
    }

    /**
     * Verifies that persisted Statement revisions control cross-operation derivative reuse.
     *
     * Given: one Statement loaded twice with the same `updated_at` and once with a newer revision.
     * Expect: the unchanged revision shares both derivatives, while the newer revision receives new derivatives.
     */
    @Test
    @DisplayName("reuses artifacts only while statement updated_at is unchanged")
    void shouldReuseDerivedArtifactsWhenStatementUpdatedAtIsUnchanged() {
        // Arrange
        UUID id = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-09-13T00:00:00Z");
        EffectiveStatement original = effective(statement(id, Effect.ALLOW, "/api/v0/users"), updatedAt);
        EffectiveStatement unchanged = effective(statement(id, Effect.ALLOW, "/api/v0/users"), updatedAt);
        EffectiveStatement changed = effective(statement(id, Effect.DENY, "/api/v0/users"), updatedAt.plusSeconds(1));

        // Act
        StatementExecutionArtifact first = this.factory.build(List.of(original), "GET", "/api/v0/users").getFirst();
        StatementExecutionArtifact second = this.factory.build(List.of(unchanged), "GET", "/api/v0/users").getFirst();
        StatementExecutionArtifact different = this.factory.build(List.of(changed), "GET", "/api/v0/users").getFirst();

        // Assert
        assertThat(second.policy()).isSameAs(first.policy());
        assertThat(second.pathMatcher()).isSameAs(first.pathMatcher());
        assertThat(different.policy()).isNotSameAs(first.policy());
        assertThat(different.pathMatcher()).isNotSameAs(first.pathMatcher());
    }

    /**
     * Verifies that the cache retains only the newest observed revision for each Statement id.
     *
     * Given: revision one, revision two, then a stale revision-one snapshot for the same Statement.
     * Expect: the stale snapshot is compiled for its own operation without resurrecting revision one in the cache, and
     * the next revision-two lookup still reuses the revision-two derivatives.
     */
    @Test
    @DisplayName("does not retain historical statement revisions")
    void shouldRetainOnlyNewestObservedStatementRevision() {
        // Arrange
        UUID id = UUID.randomUUID();
        Instant firstUpdatedAt = Instant.parse("2026-09-13T00:00:00Z");
        EffectiveStatement firstRevision = effective(statement(id, Effect.ALLOW, "/api/v0/users"), firstUpdatedAt);
        EffectiveStatement secondRevision = effective(
            statement(id, Effect.DENY, "/api/v0/users"),
            firstUpdatedAt.plusSeconds(1)
        );

        // Act
        StatementExecutionArtifact first = this.factory
            .build(List.of(firstRevision), "GET", "/api/v0/users")
            .getFirst();
        StatementExecutionArtifact second = this.factory
            .build(List.of(secondRevision), "GET", "/api/v0/users")
            .getFirst();
        StatementExecutionArtifact stale = this.factory
            .build(List.of(firstRevision), "GET", "/api/v0/users")
            .getFirst();
        StatementExecutionArtifact latest = this.factory
            .build(List.of(secondRevision), "GET", "/api/v0/users")
            .getFirst();

        // Assert
        assertThat(stale.policy()).isNotSameAs(first.policy());
        assertThat(stale.pathMatcher()).isNotSameAs(first.pathMatcher());
        assertThat(latest.policy()).isSameAs(second.policy());
        assertThat(latest.pathMatcher()).isSameAs(second.pathMatcher());
    }

    /** Verifies inactive derivatives expire while frequently accessed derivatives remain retained. */
    @Test
    @DisplayName("expires only statement artifacts that remain idle")
    void shouldExpireOnlyIdleStatementArtifacts() {
        // Arrange
        AtomicLong nanos = new AtomicLong();
        Ticker ticker = nanos::get;
        StatementArtifactFactory expiringFactory = new StatementArtifactFactory(
            new LanguageCompiler(),
            List.of(),
            ObjectAuthorizationTargetResolver.all(List.of()),
            StatementArtifactCache.expireAfterAccess(Duration.ofMinutes(5), ticker),
            StatementArtifactCache.expireAfterAccess(Duration.ofMinutes(5), ticker)
        );
        EffectiveStatement hot = effective(statement(UUID.randomUUID(), Effect.ALLOW, "/api/v0/users"), Instant.EPOCH);
        EffectiveStatement idle = effective(statement(UUID.randomUUID(), Effect.ALLOW, "/api/v0/users"), Instant.EPOCH);
        StatementExecutionArtifact hotFirst = expiringFactory.build(List.of(hot), "GET", "/api/v0/users").getFirst();
        StatementExecutionArtifact idleFirst = expiringFactory.build(List.of(idle), "GET", "/api/v0/users").getFirst();

        // Act
        nanos.addAndGet(Duration.ofMinutes(4).toNanos());
        StatementExecutionArtifact hotSecond = expiringFactory.build(List.of(hot), "GET", "/api/v0/users").getFirst();
        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        StatementExecutionArtifact hotThird = expiringFactory.build(List.of(hot), "GET", "/api/v0/users").getFirst();
        StatementExecutionArtifact idleAfterTtl = expiringFactory
            .build(List.of(idle), "GET", "/api/v0/users")
            .getFirst();

        // Assert
        assertThat(hotSecond.policy()).isSameAs(hotFirst.policy());
        assertThat(hotThird.policy()).isSameAs(hotFirst.policy());
        assertThat(idleAfterTtl.policy()).isNotSameAs(idleFirst.policy());
    }

    /**
     * Verifies that compiled Statement artifacts carry the current Language and Authorization profile contracts.
     *
     * Given: one request Statement compiled by the artifact factory.
     * Expect: its public compiled-source metadata records collision-resistant source identity, the centralized Language
     * contract, and the Authorization-owned profile fingerprint without exposing the concrete Semantic AST.
     */
    @Test
    @DisplayName("includes language and authorization profile identity in artifacts")
    void shouldIncludeLanguageAndProfileIdentityWhenStatementIsCompiled() {
        // Arrange
        EffectiveStatement statement = effective(
            statement(UUID.randomUUID(), Effect.ALLOW, "/api/v0/users"),
            Instant.EPOCH
        );

        // Act
        StatementExecutionArtifact artifact = this.factory.build(List.of(statement), "GET", "/api/v0/users").getFirst();

        // Assert
        assertThat(artifact.policy().sourceFingerprint()).hasSize(64);
        assertThat(artifact.policy().compilerFingerprint()).contains(LanguageContract.VERSION);
        assertThat(artifact.policy().profileFingerprint()).isEqualTo(
            AuthorizationCompilationProfile.requestPolicy().fingerprint()
        );
    }

    /**
     * Verifies that Object Statements use the expression entry mode required by persistence translation.
     *
     * Given: one matching Object Statement containing the Boolean expression `true`.
     * Expect: the compiled artifact records expression mode and the Object Authorization profile fingerprint.
     */
    @Test
    @DisplayName("compiles object statements with the object expression profile")
    void shouldUseObjectExpressionProfileWhenObjectStatementIsCompiled() {
        // Arrange
        ObjectAuthorizationBinding<FirstObject> binding = binding(
            FirstObject.class,
            "test:first-object",
            "field:test:first-object:name"
        );
        StatementArtifactFactory objectFactory = new StatementArtifactFactory(
            new LanguageCompiler(),
            List.of(binding),
            ObjectAuthorizationTargetResolver.all(List.of(binding))
        );
        StatementInfo statement = new StatementInfo(
            UUID.randomUUID(),
            "object_policy",
            null,
            Effect.ALLOW,
            Scope.OBJECT,
            new TargetInfo(new ApiInfo("GET", "/api/v0/users")),
            "true"
        );
        EffectiveStatement effective = effective(statement, Instant.EPOCH);

        // Act
        StatementExecutionArtifact artifact = objectFactory
            .build(List.of(effective), "GET", "/api/v0/users")
            .getFirst();
        var policy = artifact.policy(binding.resourceType(), binding.schemaFingerprint());

        // Assert
        assertThat(policy.mode()).isEqualTo(CompilationMode.EXPRESSION);
        assertThat(policy.profileFingerprint()).isEqualTo(AuthorizationCompilationProfile.objectPolicy().fingerprint());
    }

    /**
     * Verifies that statement-level conditional control flow cannot survive into Object Authorization persistence binding.
     *
     * Given: a matching Object Statement using an `if/else` program whose condition depends on `object.name`.
     * Expect: artifact compilation fails closed because Object policies accept expression source only.
     */
    @Test
    @DisplayName("rejects program control flow in object statements")
    void shouldRejectProgramControlFlowWhenObjectStatementUsesExpressionMode() {
        // Arrange
        ObjectAuthorizationBinding<FirstObject> binding = binding(
            FirstObject.class,
            "test:first-object",
            "field:test:first-object:name"
        );
        StatementArtifactFactory objectFactory = new StatementArtifactFactory(
            new LanguageCompiler(),
            List.of(binding),
            ObjectAuthorizationTargetResolver.all(List.of(binding))
        );
        StatementInfo invalid = new StatementInfo(
            UUID.randomUUID(),
            "object_conditional",
            null,
            Effect.ALLOW,
            Scope.OBJECT,
            new TargetInfo(new ApiInfo("GET", "/api/v0/users")),
            "if (object.name == \"system\") { return false; } else { return true; }"
        );
        EffectiveStatement effective = effective(invalid, Instant.EPOCH);

        // Act + Assert
        assertThatThrownBy(() -> objectFactory.build(List.of(effective), "GET", "/api/v0/users"))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("Invalid Statement policy");
    }

    /**
     * Verifies semantic policy validation is deferred until a persisted Statement target matches the operation.
     *
     * Given: malformed policy source on a Statement targeting a different request path.
     * Expect: the unrelated operation skips compilation, while a matching operation fails closed during artifact build.
     */
    @Test
    @DisplayName("compiles policy only after statement target matches")
    void shouldDeferPolicyCompilationUntilStatementTargetMatches() {
        // Arrange
        StatementInfo invalid = new StatementInfo(
            UUID.randomUUID(),
            "invalid_policy",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", "/api/v0/admin")),
            "return request.method == ;"
        );
        EffectiveStatement effective = effective(invalid, Instant.EPOCH);

        // Act
        List<StatementExecutionArtifact> unrelated = this.factory.build(List.of(effective), "GET", "/api/v0/users");

        // Assert
        assertThat(unrelated).isEmpty();
        assertThatThrownBy(() -> this.factory.build(List.of(effective), "GET", "/api/v0/admin"))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("Invalid Statement policy");
    }

    /**
     * Verifies target regex compilation is also deferred until the Statement method can match the operation.
     *
     * Given: a malformed target regex on a Statement for a different HTTP method.
     * Expect: the unrelated operation skips regex compilation, while a method-matching operation fails closed.
     */
    @Test
    @DisplayName("compiles target regex only after statement method matches")
    void shouldDeferTargetRegexCompilationUntilStatementMethodMatches() {
        // Arrange
        StatementInfo invalid = new StatementInfo(
            UUID.randomUUID(),
            "invalid_target",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("POST", "[")),
            "return true;"
        );
        EffectiveStatement effective = effective(invalid, Instant.EPOCH);

        // Act
        List<StatementExecutionArtifact> unrelated = this.factory.build(List.of(effective), "GET", "/api/v0/users");

        // Assert
        assertThat(unrelated).isEmpty();
        assertThatThrownBy(() -> this.factory.build(List.of(effective), "POST", "/api/v0/users"))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("valid regular expression");
    }

    /**
     * Verifies Java-only backtracking constructs are rejected by the bounded runtime target contract.
     *
     * Given: a persisted Statement path containing a backreference that Java regex accepts but RE2 does not.
     * Expect: target processing fails closed when the matching HTTP method executes.
     */
    @Test
    @DisplayName("rejects target regex constructs that require backtracking")
    void shouldRejectTargetRegexWhenPatternRequiresBacktracking() {
        // Arrange
        StatementInfo unsupported = new StatementInfo(
            UUID.randomUUID(),
            "unsupported_target",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", "^/(a+)\\1$")),
            "return true;"
        );
        EffectiveStatement effective = effective(unsupported, Instant.EPOCH);

        // Act + Assert
        assertThatThrownBy(() -> this.factory.build(List.of(effective), "GET", "/aaaa"))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("valid regular expression");
    }

    /**
     * Verifies unsupported engine semantics cannot silently turn a DENY target into a false negative.
     *
     * Given: a DENY target using case-insensitive Unicode-category syntax whose semantics are engine-dependent.
     * Expect: target processing fails closed before route matching can omit the DENY Statement.
     */
    @Test
    @DisplayName("fails closed before unsupported deny target semantics can diverge")
    void shouldFailClosedWhenDenyTargetUsesUnsupportedUnicodeCaseFolding() {
        // Arrange
        StatementInfo deny = new StatementInfo(
            UUID.randomUUID(),
            "unicode_deny_target",
            null,
            Effect.DENY,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", "(?i)/api/v0/users/\\p{Ll}+")),
            "return true;"
        );
        EffectiveStatement effective = effective(deny, Instant.EPOCH);

        // Act + Assert
        assertThatThrownBy(() -> this.factory.build(List.of(effective), "GET", "/api/v0/users/ADMIN"))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("valid regular expression");
    }

    private static EffectiveStatement effective(StatementInfo statement, Instant updatedAt) {
        return new EffectiveStatement(statement, updatedAt);
    }

    private static StatementInfo statement(UUID id, Effect effect, String path) {
        return new StatementInfo(
            id,
            "statement",
            null,
            effect,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", path)),
            "return true;"
        );
    }

    private static <Q> ObjectAuthorizationBinding<Q> binding(Class<Q> objectType, String resourceType, String fieldId) {
        FieldId id = FieldId.of(fieldId);
        ResourceSchema schema = ResourceSchema.of(
            ResourceType.of(resourceType),
            List.of(new Field(id, FieldPath.parse("name"), LanguageType.Scalar.STRING, false))
        );
        return new StaticObjectAuthorizationBinding<>(
            objectType,
            schema,
            List.of(new ObjectAuthorizationFieldBinding(id, "name", String.class, Set.of(ObjectAuthorizationOperator.EQ)))
        );
    }

    private static final class FirstObject {}

    private static final class SecondObject {}
}
