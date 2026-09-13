package io.taskmigo.authorization.request;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationCompilationProfile;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationEmbeddedLanguageSchemas;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.ObjectAuthorizationSchemaRegistry;
import io.taskmigo.authorization.spi.EffectiveStatement;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.EmbeddedLanguageException;
import io.taskmigo.language.EnvironmentSchema;
import io.taskmigo.language.LanguageCompiler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Service;

/// Builds executable Statement derivatives after authoritative Statement rows and revisions have been loaded.
@Service
final class StatementArtifactFactory {

    private static final String POLICY_FINGERPRINT = AuthorizationCompilationProfile.policy().fingerprint();

    private final LanguageCompiler compiler;
    private final EnvironmentSchema objectSchema;
    private final ObjectAuthorizationSchemaRegistry schemaRegistry;
    private final ConcurrentMap<UUID, CachedArtifacts> derived = new ConcurrentHashMap<>();

    StatementArtifactFactory(
        LanguageCompiler compiler,
        List<ObjectAuthorizationSchema<?>> schemas,
        ObjectAuthorizationSchemaRegistry schemaRegistry
    ) {
        this.compiler = compiler;
        this.objectSchema = AuthorizationEmbeddedLanguageSchemas.object(List.copyOf(schemas));
        this.schemaRegistry = schemaRegistry;
    }

    /// Derives executable Statements while retaining only the newest observed reusable revision per Statement id.
    List<StatementExecutionArtifact> build(Collection<EffectiveStatement> statements) {
        List<StatementExecutionArtifact> result = new ArrayList<>();
        for (EffectiveStatement effective : statements) {
            StatementInfo statement = effective.statement();
            EnvironmentSchema schema = this.schema(statement);
            ArtifactIdentity identity = new ArtifactIdentity(
                effective.updatedAt(),
                schema.fingerprint(),
                this.compiler.contractFingerprint(),
                POLICY_FINGERPRINT,
                this.applicableSchemaIdentities(statement)
            );
            DerivedArtifacts artifacts = this.derive(statement, schema, identity);
            result.add(new StatementExecutionArtifact(statement, artifacts.policy(), artifacts.pathMatcher()));
        }
        return List.copyOf(result);
    }

    private DerivedArtifacts derive(StatementInfo statement, EnvironmentSchema schema, ArtifactIdentity identity) {
        CachedArtifacts current = this.derived.get(statement.id());
        if (current != null && current.identity().equals(identity)) {
            return current.artifacts();
        }

        DerivedArtifacts compiled = this.compile(statement, schema);
        CachedArtifacts candidate = new CachedArtifacts(identity, compiled);
        CachedArtifacts retained = Objects.requireNonNull(
            this.derived.compute(statement.id(), (ignored, latest) -> {
                if (latest != null && latest.identity().equals(identity)) {
                    return latest;
                }
                if (latest != null && latest.identity().updatedAt().isAfter(identity.updatedAt())) {
                    return latest;
                }
                return candidate;
            })
        );
        return retained.identity().equals(identity) ? retained.artifacts() : compiled;
    }

    private EnvironmentSchema schema(StatementInfo statement) {
        return statement.scope() == Scope.REQUEST ? AuthorizationEmbeddedLanguageSchemas.request() : this.objectSchema;
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

    private record DerivedArtifacts(CompiledSource policy, Pattern pathMatcher) {}
}
