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
        List<String> policies = IntStream.range(0, count)
            .mapToObj(index -> policy(scope, policyType, index))
            .toList();
        if (policies.stream().distinct().count() != policies.size()) {
            throw new IllegalStateException("Benchmark policy data must contain only unique statements");
        }
        return policies;
    }

    private static String policy(Scope scope, String policyType, int index) {
        return switch (policyType) {
            case "SIMPLE" -> simplePolicy(scope, index);
            case "COMPLEX" -> complexPolicy(scope, index);
            default -> throw new IllegalArgumentException("Unknown policy type: " + policyType);
        };
    }

    private static String simplePolicy(Scope scope, int index) {
        if (scope == Scope.REQUEST) {
            return switch (index % 4) {
                case 0 -> "export default ({ request }) => request.method === 'METHOD_%d';".formatted(index);
                case 1 ->
                    "export default ({ request }) => request.pathVariables.userId === 'USER_%d';".formatted(index);
                case 2 -> "export default ({ principal }) => principal.id !== 'PRINCIPAL_%d';".formatted(index);
                default ->
                    "export default ({ request }) => request.pathVariables.resourceId !== 'RESOURCE_%d';".formatted(
                        index
                    );
            };
        }
        return switch (index % 4) {
            case 0 -> "export default ({ object }) => object.score >= %d;".formatted(index);
            case 1 -> "export default ({ object }) => object.ownerId === 'OWNER_%d';".formatted(index);
            case 2 -> "export default ({ object }) => object.status !== 'STATUS_%d';".formatted(index);
            default -> "export default ({ object }) => object.version < %d;".formatted(index + 1);
        };
    }

    private static String complexPolicy(Scope scope, int index) {
        if (scope == Scope.REQUEST) {
            return complexRequestPolicy(index);
        }
        return complexObjectPolicy(index);
    }

    private static String complexRequestPolicy(int index) {
        return switch (index % 4) {
            case 0 ->
                """
                export default ({ request, principal }) => {
                  const expectedMethod = 'METHOD_%d';
                  const reservedUser = 'USER_%d';
                  if (request.method === expectedMethod && principal.id !== '') {
                    return request.pathVariables.userId === principal.id
                      && request.pathVariables.userId !== reservedUser;
                  }
                  return false;
                };
                """.formatted(index, index);
            case 1 ->
                """
                export default ({ request, principal }) => {
                  const expectedResource = 'RESOURCE_%d';
                  if (request.pathVariables.resourceId === expectedResource) {
                    return request.method !== 'DELETE_%d' && principal.id !== '';
                  }
                  return false;
                };
                """.formatted(index, index);
            case 2 ->
                """
                export default ({ request, principal }) => {
                  const lowerBound = %d;
                  const upperBound = %d;
                  if (principal.rank >= lowerBound) {
                    return principal.rank < upperBound && request.method === 'PATCH_%d';
                  }
                  return false;
                };
                """.formatted(index, index + 100, index);
            default ->
                """
                export default ({ request, principal }) => {
                  const expectedOwner = 'OWNER_%d';
                  if (request.pathVariables.ownerId === expectedOwner || principal.id === expectedOwner) {
                    return request.method === 'GET_%d' && request.pathVariables.userId !== '';
                  }
                  return false;
                };
                """.formatted(index, index);
        };
    }

    private static String complexObjectPolicy(int index) {
        return switch (index % 4) {
            case 0 ->
                """
                export default ({ object, principal }) => {
                  const threshold = 40 + %d;
                  if (object.enabled === true && object.ownerId === principal.id) {
                    return object.score >= threshold && object.kind !== 'KIND_%d';
                  }
                  return false;
                };
                """.formatted(index, index);
            case 1 ->
                """
                export default ({ object, principal }) => {
                  const expectedStatus = 'STATUS_%d';
                  if (object.status === expectedStatus) {
                    return object.ownerId === principal.id && object.version >= %d;
                  }
                  return false;
                };
                """.formatted(index, index);
            case 2 ->
                """
                export default ({ object, principal }) => {
                  const minimumScore = %d;
                  const maximumScore = %d;
                  if (object.score >= minimumScore && object.score < maximumScore) {
                    return object.enabled !== false && principal.id !== '';
                  }
                  return false;
                };
                """.formatted(index, index + 100);
            default ->
                """
                export default ({ object, principal }) => {
                  const expectedOwner = 'OWNER_%d';
                  if (object.ownerId === expectedOwner || object.ownerId === principal.id) {
                    return object.kind === 'KIND_%d' && object.enabled === true;
                  }
                  return false;
                };
                """.formatted(index, index);
        };
    }
}
