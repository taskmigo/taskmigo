package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.policy.PolicyIr;
import io.taskmigo.auth.authorization.statement.ApiInfo;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.TargetInfo;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/// Benchmarks policy compilation and executable Statement derivation for a configurable authorization state.
@BenchmarkMode(Mode.AverageTime)
public class StatementArtifactFactoryBenchmark {

    /// Measures compiling policy IR and path matchers directly for every Statement.
    @Benchmark
    public void compileWithoutCache(BenchmarkState state, Blackhole blackhole) {
        List<StatementExecutionArtifact> artifacts = new ArrayList<>(state.statements.size());
        for (StatementInfo statement : state.statements) {
            PolicyIr policy = state.compiler.compile(statement.policy(), statement.scope());
            Pattern pathMatcher = Pattern.compile(statement.target().api().path());
            artifacts.add(new StatementExecutionArtifact(statement, policy, pathMatcher));
        }
        blackhole.consume(artifacts);
    }

    /// Measures the first factory build, including fingerprinting and all cold artifact derivation.
    @Benchmark
    public void factoryCold(BenchmarkState state, Blackhole blackhole) {
        StatementArtifactFactory factory = new StatementArtifactFactory(state.compiler);
        blackhole.consume(factory.build(state.statements));
    }

    /// Measures rebuilding the same Statements after all derived artifacts are cached.
    @Benchmark
    public void factoryWarm(BenchmarkState state, Blackhole blackhole) {
        blackhole.consume(state.warmFactory.build(state.statements));
    }

    /// Measures two alternating one-Statement changes while the other artifacts remain cached.
    @Benchmark
    @OperationsPerInvocation(2)
    public void factoryAfterStatementChange(BenchmarkState state, Blackhole blackhole) {
        blackhole.consume(state.warmFactory.build(state.changedStatements));
        blackhole.consume(state.warmFactory.build(state.statements));
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class BenchmarkState {

        @Param({ "statement-artifact" })
        @SuppressWarnings({ "CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal" })
        private String testName = "statement-artifact";

        @Param({ "REQUEST", "OBJECT" })
        @SuppressWarnings({ "CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal" })
        private String scopeName = "REQUEST";

        @Param({ "SIMPLE", "COMPLEX" })
        @SuppressWarnings({ "CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal" })
        private String policyType = "SIMPLE";

        @Param({ "500", "5000", "10000" })
        @SuppressWarnings({ "CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal" })
        private String statementCount = "10000";

        private final JavaScriptPolicyCompiler compiler = new JavaScriptPolicyCompiler();
        private List<StatementInfo> statements = List.of();
        private List<StatementInfo> changedStatements = List.of();
        private StatementArtifactFactory warmFactory = new StatementArtifactFactory(this.compiler);

        @Setup
        public void setUp() {
            Scope scope = Scope.valueOf(this.scopeName);
            int count = Integer.parseInt(this.statementCount);
            this.statements = statements(this.testName, scope, this.policyType, count);
            this.changedStatements = changed(this.statements, count / 2);
            this.warmFactory = new StatementArtifactFactory(this.compiler);
            this.warmFactory.build(this.statements);
        }
    }

    private static List<StatementInfo> statements(String testName, Scope scope, String policyType, int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(index ->
                new StatementInfo(
                    UUID.nameUUIDFromBytes(
                        (testName + ":" + scope + ":" + policyType + ":" + index).getBytes(StandardCharsets.UTF_8)
                    ),
                    "benchmark-statement-" + index,
                    null,
                    Effect.ALLOW,
                    scope,
                    new TargetInfo(new ApiInfo(index % 2 == 0 ? "GET" : "POST", "/api/v0/benchmark/" + index)),
                    policy(scope, policyType)
                )
            )
            .toList();
    }

    private static List<StatementInfo> changed(List<StatementInfo> original, int changedIndex) {
        List<StatementInfo> result = new ArrayList<>(original);
        StatementInfo statement = result.get(changedIndex);
        result.set(
            changedIndex,
            new StatementInfo(
                statement.id(),
                statement.name(),
                statement.description(),
                Effect.DENY,
                statement.scope(),
                statement.target(),
                statement.policy()
            )
        );
        return List.copyOf(result);
    }

    private static String policy(Scope scope, String policyType) {
        if ("SIMPLE".equals(policyType)) {
            return scope == Scope.REQUEST
                ? "export default ({ request }) => request.method === 'GET';"
                : "export default ({ object }) => object.enabled === true;";
        }
        return scope == Scope.REQUEST
            ? """
              export default ({ request, principal }) => {
                const limit = 40 + 2;
                if (request.method === 'GET' && principal.id !== '') {
                  return request.pathVariables.userId === principal.id && limit > 0;
                }
                return false;
              };
              """
            : """
              export default ({ object, principal }) => {
                const threshold = 40 + 2;
                if (object.enabled === true) {
                  return object.score >= threshold && object.ownerId === principal.id;
                }
                return false;
              };
              """;
    }
}
