package io.taskmigo.benchmarks.authorization;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.openjdk.jmh.annotations.Scope.Thread;

import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.policy.PolicyIr;
import io.taskmigo.auth.authorization.statement.Scope;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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

    private static final int DATASET_SIZE = 500;
    private static final String DATASET_ROOT = "/io/taskmigo/benchmarks/authorization/";
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");

    /// Measures compiling one deterministic batch of authorization policies.
    @Benchmark
    public void compileBatch(BenchmarkState state, Blackhole blackhole) {
        List<PolicyIr> compiled = new ArrayList<>(state.policies.size());
        for (String source : state.policies) {
            compiled.add(state.compiler.compile(source, state.scope));
        }
        blackhole.consume(compiled);
    }

    /// Holds the immutable compiler and loaded policy source used by each benchmark thread.
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

        /// Loads deterministic policy sources before JMH starts measuring the benchmark.
        @Setup
        public void setUp() {
            this.scope = Scope.valueOf(this.scopeName);
            this.policies = policies(this.scope, this.policyType, Integer.parseInt(this.statementCount));
        }
    }

    private static List<String> policies(Scope scope, String policyType, int count) {
        if (count < 1 || count > DATASET_SIZE) {
            throw new IllegalArgumentException("statementCount must be between 1 and " + DATASET_SIZE);
        }
        List<String[]> rows = rows(datasetResource(policyType));
        if (rows.size() != DATASET_SIZE) {
            throw new IllegalStateException("Benchmark dataset must contain exactly " + DATASET_SIZE + " statements");
        }
        IntStream.range(0, rows.size()).forEach(index -> validateRow(rows.get(index), index));
        validateDataset(rows, policyType);
        int column = scope == Scope.REQUEST ? 1 : 2;
        List<String> policies = rows.subList(0, count).stream().map(row -> row[column]).toList();
        if (policies.stream().distinct().count() != policies.size()) {
            throw new IllegalStateException("Benchmark dataset must contain only unique statements");
        }
        return policies;
    }

    private static String datasetResource(String policyType) {
        return switch (policyType) {
            case "SIMPLE" -> DATASET_ROOT + "simple-statements.tsv";
            case "COMPLEX" -> DATASET_ROOT + "complex-statements.tsv";
            default -> throw new IllegalArgumentException("Unknown policy type: " + policyType);
        };
    }

    private static int expectedStructuralFamilies(String policyType) {
        return switch (policyType) {
            case "SIMPLE" -> 100;
            case "COMPLEX" -> 125;
            default -> throw new IllegalArgumentException("Unknown policy type: " + policyType);
        };
    }

    private static List<String[]> rows(String resource) {
        var stream = JavaScriptPolicyCompilerBenchmark.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing benchmark dataset: " + resource);
        }
        try (var reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            String header = reader.readLine();
            if (!"id\trequest\tobject".equals(header)) {
                throw new IllegalStateException("Invalid benchmark dataset header: " + resource);
            }
            return reader.lines().map(line -> line.split("\\t", -1)).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read benchmark dataset: " + resource, exception);
        }
    }

    private static void validateDataset(List<String[]> rows, String policyType) {
        int expectedFamilies = expectedStructuralFamilies(policyType);
        for (int column = 1; column <= 2; column++) {
            if (rows.stream().map(row -> row[column]).distinct().count() != DATASET_SIZE) {
                throw new IllegalStateException("Benchmark dataset must contain only unique statements");
            }
            long families = rows.stream().map(row -> structuralFingerprint(row[column])).distinct().count();
            if (families != expectedFamilies) {
                throw new IllegalStateException(
                    "Benchmark dataset must contain exactly " + expectedFamilies + " structural families"
                );
            }
        }
    }

    private static String structuralFingerprint(String source) {
        String normalized = STRING_LITERAL.matcher(source).replaceAll("'#'");
        normalized = NUMBER_LITERAL.matcher(normalized).replaceAll("#");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private static void validateRow(String[] row, int index) {
        if (row.length != 3 || row[1].isBlank() || row[2].isBlank()) {
            throw new IllegalStateException("Invalid benchmark dataset row: " + index);
        }
        if (!row[0].equals("%03d".formatted(index))) {
            throw new IllegalStateException("Benchmark dataset IDs must be contiguous from 000 to 499");
        }
    }
}
