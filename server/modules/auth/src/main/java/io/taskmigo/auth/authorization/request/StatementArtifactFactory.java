package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.policy.PolicyIr;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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

    private final JavaScriptPolicyCompiler compiler;
    private final ConcurrentMap<UUID, CachedArtifacts> derived = new ConcurrentHashMap<>();

    /// Creates a factory whose cache contains only compiled policy and matcher derivatives.
    public StatementArtifactFactory(JavaScriptPolicyCompiler compiler) {
        this.compiler = compiler;
    }

    /// Derives executable Statements from the exact rows returned by the current authorization resolution.
    public List<StatementExecutionArtifact> build(Collection<StatementInfo> statements) {
        Map<PolicyKey, PolicyIr> policies = new HashMap<>();
        Map<String, Pattern> pathMatchers = new HashMap<>();
        List<StatementExecutionArtifact> result = new ArrayList<>(statements.size());
        for (StatementInfo statement : statements) {
            String fingerprint = fingerprint(statement);
            CachedArtifacts cached = this.derived.compute(statement.id(), (ignored, current) ->
                current != null && current.fingerprint().equals(fingerprint)
                    ? current
                    : new CachedArtifacts(fingerprint, this.compile(statement, policies, pathMatchers))
            );
            result.add(
                new StatementExecutionArtifact(statement, cached.artifacts().policy(), cached.artifacts().pathMatcher())
            );
        }
        return List.copyOf(result);
    }

    private DerivedArtifacts compile(
        StatementInfo statement,
        Map<PolicyKey, PolicyIr> policies,
        Map<String, Pattern> pathMatchers
    ) {
        PolicyKey policyKey = new PolicyKey(statement.policy(), statement.scope());
        PolicyIr policy = policies.computeIfAbsent(policyKey, ignored ->
            this.compiler.compile(statement.policy(), statement.scope())
        );
        String path = statement.target().api().path();
        Pattern pathMatcher = pathMatchers.computeIfAbsent(path, StatementArtifactFactory::compilePath);
        return new DerivedArtifacts(policy, pathMatcher);
    }

    private static Pattern compilePath(String path) {
        try {
            return Pattern.compile(path);
        } catch (PatternSyntaxException exception) {
            throw new AuthorizationException("Statement target path is not a valid regular expression");
        }
    }

    private static String fingerprint(StatementInfo statement) {
        StringBuilder state = new StringBuilder();
        append(state, statement.id());
        append(state, statement.name());
        append(state, statement.description());
        append(state, statement.effect());
        append(state, statement.scope());
        append(state, statement.target().api().method());
        append(state, statement.target().api().path());
        append(state, statement.policy());
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

    private record PolicyKey(String source, Scope scope) {}

    private record DerivedArtifacts(PolicyIr policy, Pattern pathMatcher) {}
}
