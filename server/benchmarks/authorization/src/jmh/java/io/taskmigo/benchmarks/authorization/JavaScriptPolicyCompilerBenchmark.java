package io.taskmigo.benchmarks.authorization;

import static org.openjdk.jmh.annotations.Scope.Thread;

import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.policy.PolicyIr;
import io.taskmigo.auth.authorization.statement.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/// Benchmarks compilation of representative request and object authorization policies.
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class JavaScriptPolicyCompilerBenchmark {

    /// Measures compiling one deterministic batch of authorization policies.
    @Benchmark
    public void compileBatch(BenchmarkState state, Blackhole blackhole) {
        List<PolicyIr> compiled = new ArrayList<>(state.policies.size());
        for (String source : state.policies) {
            compiled.add(state.compiler.compile(source, state.scope));
        }
        blackhole.consume(compiled);
    }

    /// Holds the immutable compiler and generated policy source used by each benchmark thread.
    @State(Thread)
    public static class BenchmarkState {

        @Param({ "REQUEST", "OBJECT" })
        @SuppressWarnings({ "CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal" })
        private String scopeName = "REQUEST";

        @Param({ "SIMPLE", "COMPLEX" })
        @SuppressWarnings({ "CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal" })
        private String policyType = "SIMPLE";

        @Param({ "500" })
        @SuppressWarnings({ "CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal" })
        private String statementCount = "500";

        private final JavaScriptPolicyCompiler compiler = new JavaScriptPolicyCompiler();
        private Scope scope = Scope.REQUEST;
        private List<String> policies = List.of();

        /// Creates deterministic policy sources before JMH starts measuring the benchmark.
        @Setup
        public void setUp() {
            this.scope = Scope.valueOf(this.scopeName);
            this.policies = policies(this.scope, this.policyType, Integer.parseInt(this.statementCount));
        }
    }

    private static List<String> policies(Scope scope, String policyType, int count) {
        return IntStream.range(0, count)
            .mapToObj(index -> policy(scope, policyType, index))
            .toList();
    }

    private static String policy(Scope scope, String policyType, int index) {
        if ("SIMPLE".equals(policyType)) {
            return scope == Scope.REQUEST
                ? "export default ({ request }) => request.method === 'GET';"
                : "export default ({ object }) => object.enabled === true;";
        }
        if ("COMPLEX".equals(policyType)) {
            return scope == Scope.REQUEST
                ? """
                  export default ({ request, principal }) => {
                    const expectedMethod = 'GET';
                    if (request.method === expectedMethod && principal.id !== '') {
                      return request.pathVariables.userId === principal.id && %d >= 0;
                    }
                    return false;
                  };
                  """.formatted(index)
                : """
                  export default ({ object, principal }) => {
                    const threshold = 40 + 2;
                    if (object.enabled === true) {
                      return object.score >= threshold && object.ownerId === principal.id && %d >= 0;
                    }
                    return false;
                  };
                  """.formatted(index);
        }
        throw new IllegalArgumentException("Unknown policy type: " + policyType);
    }
}
