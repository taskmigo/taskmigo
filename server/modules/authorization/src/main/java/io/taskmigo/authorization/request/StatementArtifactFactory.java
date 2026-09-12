package io.taskmigo.authorization.request;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationCompilationProfile;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationEmbeddedLanguageSchemas;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.ObjectAuthorizationSchemaRegistry;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.EmbeddedLanguageException;
import io.taskmigo.language.EnvironmentSchema;
import io.taskmigo.language.LanguageCompiler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Service;

/// Builds executable Statement derivatives after the authoritative Statement rows have been loaded.
@Service
public final class StatementArtifactFactory {

    private static final String POLICY_FINGERPRINT = AuthorizationCompilationProfile.policy().fingerprint();

    private final LanguageCompiler compiler;
    private final EnvironmentSchema objectSchema;
    private final ObjectAuthorizationSchemaRegistry schemaRegistry;
    private final ConcurrentMap<CacheKey, DerivedArtifacts> derived = new ConcurrentHashMap<>();

    /// Creates a factory whose cache contains only compiled policy and matcher derivatives.
    public StatementArtifactFactory(
        LanguageCompiler compiler,
        List<ObjectAuthorizationSchema<?>> schemas,
        ObjectAuthorizationSchemaRegistry schemaRegistry
    ) {
        this.compiler = compiler;
        this.objectSchema = AuthorizationEmbeddedLanguageSchemas.object(List.copyOf(schemas));
        this.schemaRegistry = schemaRegistry;
    }

    /// Derives executable Statements from the exact rows returned by the current authorization resolution.
    public List<StatementExecutionArtifact> build(Collection<StatementInfo> statements) {
        List<StatementExecutionArtifact> result = new ArrayList<>();
        for (StatementInfo statement : statements) {
            EnvironmentSchema schema =
                statement.scope() == Scope.REQUEST ? AuthorizationEmbeddedLanguageSchemas.request() : this.objectSchema;
            CacheKey key = new CacheKey(
                statement,
                schema.fingerprint(),
                this.compiler.contractFingerprint(),
                POLICY_FINGERPRINT,
                this.applicableSchemaIdentities(statement)
            );
            DerivedArtifacts cached = Objects.requireNonNull(
                this.derived.computeIfAbsent(key, ignored -> this.compile(statement, schema))
            );
            result.add(new StatementExecutionArtifact(statement, cached.policy(), cached.pathMatcher()));
        }
        return List.copyOf(result);
    }

    private DerivedArtifacts compile(StatementInfo statement, EnvironmentSchema schema) {
        try {
            return new DerivedArtifacts(
                this.compiler.compile(statement.policy(), schema, AuthorizationCompilationProfile.policy()),
                Pattern.compile(statement.target().api().path())
            );
        } catch (PatternSyntaxException exception) {
            throw new AuthorizationException("Statement target path is not a valid regular expression");
        } catch (EmbeddedLanguageException exception) {
            throw new AuthorizationException("Invalid Statement policy: " + exception.getMessage());
        }
    }

    private List<String> applicableSchemaIdentities(StatementInfo statement) {
        if (statement.scope() != Scope.OBJECT) {
            return List.of();
        }
        return this.schemaRegistry
            .applicable(statement.target().api().method(), statement.target().api().path())
            .stream()
            .map(ObjectAuthorizationSchema::identity)
            .sorted()
            .toList();
    }

    private record CacheKey(
        StatementInfo statement,
        String schemaFingerprint,
        String compilerFingerprint,
        String profileFingerprint,
        List<String> applicableSchemaIdentities
    ) {
        private CacheKey {
            applicableSchemaIdentities = List.copyOf(applicableSchemaIdentities);
        }
    }

    private record DerivedArtifacts(CompiledSource policy, Pattern pathMatcher) {}
}
