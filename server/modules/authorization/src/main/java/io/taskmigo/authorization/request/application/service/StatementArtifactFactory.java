package io.taskmigo.authorization.request.application.service;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationCompilationProfile;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationEmbeddedLanguageSchemas;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.request.application.port.out.EffectiveStatement;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import io.taskmigo.language.CompilationProfile;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.EmbeddedLanguageException;
import io.taskmigo.language.EnvironmentSchema;
import io.taskmigo.language.LanguageCompiler;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/// Builds executable Statement derivatives after authoritative Statement rows and revisions have been loaded.
public final class StatementArtifactFactory {

    private static final Duration CACHE_IDLE_TTL = Duration.ofHours(1);

    private final LanguageCompiler compiler;
    private final EnvironmentSchema objectSchema;
    private final ObjectAuthorizationTargetResolver targetResolver;
    private final StatementArtifactCache<CachedTargetMatcher> targetMatchers;
    private final StatementArtifactCache<CachedArtifacts> derived;

    public StatementArtifactFactory(
        LanguageCompiler compiler,
        List<ObjectAuthorizationSchema<?>> schemas,
        ObjectAuthorizationTargetResolver targetResolver
    ) {
        this(
            compiler,
            schemas,
            targetResolver,
            StatementArtifactCache.expireAfterAccess(CACHE_IDLE_TTL),
            StatementArtifactCache.expireAfterAccess(CACHE_IDLE_TTL)
        );
    }

    StatementArtifactFactory(
        LanguageCompiler compiler,
        List<ObjectAuthorizationSchema<?>> schemas,
        ObjectAuthorizationTargetResolver targetResolver,
        StatementArtifactCache<CachedTargetMatcher> targetMatchers,
        StatementArtifactCache<CachedArtifacts> derived
    ) {
        this.compiler = compiler;
        this.objectSchema = AuthorizationEmbeddedLanguageSchemas.object(List.copyOf(schemas));
        this.targetResolver = targetResolver;
        this.targetMatchers = targetMatchers;
        this.derived = derived;
    }

    /// Derives only target-matching Statements so policy semantics remain deferred until the current operation needs them.
    public List<StatementExecutionArtifact> build(
        Collection<EffectiveStatement> statements,
        String requestMethod,
        String requestPath
    ) {
        List<StatementExecutionArtifact> result = new ArrayList<>();
        for (EffectiveStatement effective : statements) {
            StatementInfo statement = effective.statement();
            if (!methodMatches(statement, requestMethod)) {
                continue;
            }
            StatementTargetPathMatcher pathMatcher = this.targetMatcher(effective);
            if (!pathMatcher.matches(requestPath)) {
                continue;
            }

            EnvironmentSchema schema = this.schema(statement);
            CompilationProfile profile = profile(statement);
            ArtifactIdentity identity = new ArtifactIdentity(
                effective.updatedAt(),
                schema.fingerprint(),
                this.compiler.contractFingerprint(),
                profile.fingerprint(),
                this.applicableSchemaIdentities(statement, pathMatcher)
            );
            DerivedArtifacts artifacts = this.derive(statement, schema, pathMatcher, profile, identity);
            result.add(new StatementExecutionArtifact(statement, artifacts.policy(), artifacts.pathMatcher()));
        }
        return List.copyOf(result);
    }

    private StatementTargetPathMatcher targetMatcher(EffectiveStatement effective) {
        StatementInfo statement = effective.statement();
        TargetMatcherIdentity identity = new TargetMatcherIdentity(
            effective.updatedAt(),
            statement.target().api().path()
        );
        CachedTargetMatcher retained = this.targetMatchers.getOrCreate(
            statement.id(),
            identity.updatedAt(),
            cached -> cached.identity().updatedAt(),
            cached -> cached.identity().equals(identity),
            () -> new CachedTargetMatcher(identity, StatementTargetPathMatcher.compile(identity.expression()))
        );
        return retained.matcher();
    }

    private DerivedArtifacts derive(
        StatementInfo statement,
        EnvironmentSchema schema,
        StatementTargetPathMatcher pathMatcher,
        CompilationProfile profile,
        ArtifactIdentity identity
    ) {
        CachedArtifacts retained = this.derived.getOrCreate(
            statement.id(),
            identity.updatedAt(),
            cached -> cached.identity().updatedAt(),
            cached -> cached.identity().equals(identity),
            () -> new CachedArtifacts(identity, this.compile(statement, schema, pathMatcher, profile))
        );
        return retained.artifacts();
    }

    private EnvironmentSchema schema(StatementInfo statement) {
        return statement.scope() == Scope.REQUEST ? AuthorizationEmbeddedLanguageSchemas.request() : this.objectSchema;
    }

    private static CompilationProfile profile(StatementInfo statement) {
        return switch (statement.scope()) {
            case REQUEST -> AuthorizationCompilationProfile.requestPolicy();
            case OBJECT -> AuthorizationCompilationProfile.objectPolicy();
        };
    }

    private DerivedArtifacts compile(
        StatementInfo statement,
        EnvironmentSchema schema,
        StatementTargetPathMatcher pathMatcher,
        CompilationProfile profile
    ) {
        try {
            return new DerivedArtifacts(this.compiler.compile(statement.policy(), schema, profile), pathMatcher);
        } catch (EmbeddedLanguageException exception) {
            throw new AuthorizationException("Invalid Statement policy: " + exception.getMessage());
        }
    }

    private static boolean methodMatches(StatementInfo statement, String requestMethod) {
        return statement.target().api().method().equals("*") || statement.target().api().method().equals(requestMethod);
    }

    private List<String> applicableSchemaIdentities(StatementInfo statement, StatementTargetPathMatcher pathMatcher) {
        if (statement.scope() != Scope.OBJECT) {
            return List.of();
        }
        return this.targetResolver
            .applicable(statement.target().api().method(), pathMatcher)
            .stream()
            .map(ObjectAuthorizationSchema::identity)
            .sorted()
            .toList();
    }

    private record TargetMatcherIdentity(Instant updatedAt, String expression) {}

    private record CachedTargetMatcher(TargetMatcherIdentity identity, StatementTargetPathMatcher matcher) {}

    private record ArtifactIdentity(
        Instant updatedAt,
        String schemaFingerprint,
        String compilerFingerprint,
        String profileFingerprint,
        List<String> applicableSchemaIdentities
    ) {
        private ArtifactIdentity {
            applicableSchemaIdentities = List.copyOf(applicableSchemaIdentities);
        }
    }

    private record CachedArtifacts(ArtifactIdentity identity, DerivedArtifacts artifacts) {}

    private record DerivedArtifacts(CompiledSource policy, StatementTargetPathMatcher pathMatcher) {}
}
