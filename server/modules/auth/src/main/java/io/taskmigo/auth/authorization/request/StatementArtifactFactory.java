package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.object.AuthorizationObjectQueryDialect;
import io.taskmigo.auth.authorization.policy.AuthorizationPolicySchemas;
import io.taskmigo.auth.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.policy.EnvironmentSchema;
import io.taskmigo.policy.PolicyCompiler;
import io.taskmigo.policy.PolicyException;
import io.taskmigo.policy.PolicyIr;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/// Builds executable Statement derivatives after the authoritative Statement rows have been loaded.
@Service
public final class StatementArtifactFactory {

    private final PolicyCompiler compiler;
    private final List<AuthorizationObjectQueryDialect> dialects;
    private final ConcurrentMap<CacheKey, CachedArtifacts> derived = new ConcurrentHashMap<>();

    /// Creates a factory whose cache contains only compiled policy and matcher derivatives.
    public StatementArtifactFactory(PolicyCompiler compiler, List<AuthorizationObjectQueryDialect> dialects) {
        this.compiler = compiler;
        this.dialects = List.copyOf(dialects);
    }

    /// Derives executable Statements from the exact rows returned by the current authorization resolution.
    public List<StatementExecutionArtifact> build(Collection<StatementInfo> statements) {
        List<StatementExecutionArtifact> result = new ArrayList<>();
        for (StatementInfo statement : statements) {
            EnvironmentSchema schema =
                statement.scope() == io.taskmigo.auth.authorization.statement.Scope.REQUEST
                    ? AuthorizationPolicySchemas.request()
                    : AuthorizationPolicySchemas.object(this.dialects);
            String fingerprint = fingerprint(statement, schema);
            CacheKey key = new CacheKey(statement.id(), schema.fingerprint(), fingerprint);
            CachedArtifacts cached = this.derived.compute(key, (ignored, current) ->
                current != null && current.fingerprint().equals(fingerprint)
                    ? current
                    : new CachedArtifacts(fingerprint, this.compile(statement, schema))
            );
            result.add(
                new StatementExecutionArtifact(statement, cached.artifacts().policy(), cached.artifacts().pathMatcher())
            );
        }
        return List.copyOf(result);
    }

    private DerivedArtifacts compile(StatementInfo statement, EnvironmentSchema schema) {
        try {
            return new DerivedArtifacts(
                this.compiler.compile(statement.policy(), schema),
                Pattern.compile(statement.target().api().path())
            );
        } catch (PatternSyntaxException exception) {
            throw new AuthorizationException("Statement target path is not a valid regular expression");
        } catch (PolicyException exception) {
            throw new AuthorizationException("Invalid Statement policy: " + exception.getMessage());
        }
    }

    private String fingerprint(StatementInfo statement, EnvironmentSchema schema) {
        StringBuilder state = new StringBuilder();
        append(state, statement.id());
        append(state, statement.name());
        append(state, statement.description());
        append(state, statement.effect());
        append(state, statement.scope());
        append(state, statement.target().api().method());
        append(state, statement.target().api().path());
        append(state, statement.policy());
        append(state, PolicyIr.LANGUAGE_VERSION);
        append(state, schema.fingerprint());
        append(state, this.compiler.contractFingerprint());
        if (statement.scope() == io.taskmigo.auth.authorization.statement.Scope.OBJECT) {
            this.dialects.forEach(dialect -> {
                append(state, dialect.method());
                append(state, dialect.path());
                append(state, dialect.fields());
                append(state, dialect.nullableFields());
                append(state, dialect.operators());
            });
        }
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(state.toString().getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void append(StringBuilder state, @Nullable Object value) {
        if (value == null) {
            state.append("-1:");
            return;
        }
        String encoded = value.toString();
        state.append(encoded.length()).append(':').append(encoded);
    }

    private record CachedArtifacts(String fingerprint, DerivedArtifacts artifacts) {}

    private record CacheKey(UUID statementId, String schemaFingerprint, String statementFingerprint) {}

    private record DerivedArtifacts(PolicyIr policy, Pattern pathMatcher) {}
}
