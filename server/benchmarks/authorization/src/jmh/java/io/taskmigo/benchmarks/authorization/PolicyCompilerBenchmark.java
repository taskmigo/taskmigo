package io.taskmigo.benchmarks.authorization;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.openjdk.jmh.annotations.Scope.Thread;

import io.taskmigo.policy.EnvironmentSchema;
import io.taskmigo.policy.PolicyCompiler;
import io.taskmigo.policy.PolicyIr;
import io.taskmigo.policy.PolicyType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class PolicyCompilerBenchmark {

    private static final int DATASET_SIZE = 500;
    private static final String DATASET_ROOT = "/io/taskmigo/benchmarks/authorization/";
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");

    /// Measures compiling one deterministic batch of authorization policies.
    @Benchmark
    public void compileBatch(BenchmarkState state, Blackhole blackhole) {
        List<PolicyIr> compiled = new ArrayList<>(state.policies.size());
        for (String source : state.policies) {
            compiled.add(state.compiler.compile(source, state.schema));
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

        private final PolicyCompiler compiler = new PolicyCompiler();
        private EnvironmentSchema schema = schema("REQUEST");
        private List<String> policies = List.of();

        /// Loads deterministic policy sources before JMH starts measuring the benchmark.
        @Setup
        public void setUp() {
            this.schema = schema(this.scopeName);
            this.policies = policies(this.scopeName, this.policyType, Integer.parseInt(this.statementCount));
        }
    }

    private static List<String> policies(String scope, String policyType, int count) {
        if (count < 1 || count > DATASET_SIZE) {
            throw new IllegalArgumentException("statementCount must be between 1 and " + DATASET_SIZE);
        }
        List<String[]> rows = rows(datasetResource(policyType));
        if (rows.size() != DATASET_SIZE) {
            throw new IllegalStateException("Benchmark dataset must contain exactly " + DATASET_SIZE + " statements");
        }
        IntStream.range(0, rows.size()).forEach(index -> validateRow(rows.get(index), index));
        validateDataset(rows, policyType);
        int column = "REQUEST".equals(scope) ? 1 : 2;
        List<String> policies = rows
            .subList(0, count)
            .stream()
            .map(row -> row[column])
            .toList();
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
        var stream = PolicyCompilerBenchmark.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing benchmark dataset: " + resource);
        }
        try (var reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            String header = reader.readLine();
            if (!"id\trequest\tobject".equals(header)) {
                throw new IllegalStateException("Invalid benchmark dataset header: " + resource);
            }
            return reader
                .lines()
                .map(line -> line.split("\\t", -1))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read benchmark dataset: " + resource, exception);
        }
    }

    private static void validateDataset(List<String[]> rows, String policyType) {
        int expectedFamilies = expectedStructuralFamilies(policyType);
        validateDatasetColumn(rows, 1, expectedFamilies);
        validateDatasetColumn(rows, 2, expectedFamilies);
    }

    private static void validateDatasetColumn(List<String[]> rows, int column, int expectedFamilies) {
        if (
            rows
                .stream()
                .map(row -> row[column])
                .distinct()
                .count() != DATASET_SIZE
        ) {
            throw new IllegalStateException("Benchmark dataset must contain only unique statements");
        }
        long families = rows
            .stream()
            .map(row -> structuralFingerprint(row[column]))
            .distinct()
            .count();
        if (families != expectedFamilies) {
            throw new IllegalStateException(
                "Benchmark dataset must contain exactly " + expectedFamilies + " structural families"
            );
        }
    }

    /// Removes literal-only differences so changing a value does not count as structural diversity.
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

    private static EnvironmentSchema schema(String scope) {
        MapBuilder roots = new MapBuilder();
        roots.add(
            "principal",
            Map.of(
                "id",
                field(PolicyType.Scalar.STRING),
                "username",
                field(PolicyType.Scalar.STRING),
                "role",
                field(PolicyType.Scalar.STRING),
                "tenantId",
                field(PolicyType.Scalar.STRING),
                "teamId",
                field(PolicyType.Scalar.STRING),
                "kind",
                field(PolicyType.Scalar.STRING),
                "active",
                field(PolicyType.Scalar.BOOL),
                "level",
                field(PolicyType.Scalar.NUMBER),
                "rank",
                field(PolicyType.Scalar.NUMBER),
                "version",
                field(PolicyType.Scalar.NUMBER)
            )
        );
        if ("REQUEST".equals(scope)) {
            roots.add(
                "request",
                Map.of(
                    "method",
                    field(PolicyType.Scalar.STRING),
                    "path",
                    field(PolicyType.Scalar.STRING),
                    "pathVariables",
                    dynamicString(),
                    "version",
                    field(PolicyType.Scalar.NUMBER),
                    "sequence",
                    field(PolicyType.Scalar.NUMBER)
                )
            );
        } else {
            roots.add(
                "object",
                Map.of(
                    "ownerId",
                    field(PolicyType.Scalar.STRING),
                    "status",
                    field(PolicyType.Scalar.STRING),
                    "kind",
                    field(PolicyType.Scalar.STRING),
                    "tenantId",
                    field(PolicyType.Scalar.STRING),
                    "visibility",
                    field(PolicyType.Scalar.STRING),
                    "enabled",
                    field(PolicyType.Scalar.BOOL),
                    "score",
                    field(PolicyType.Scalar.NUMBER),
                    "version",
                    field(PolicyType.Scalar.NUMBER),
                    "priority",
                    field(PolicyType.Scalar.NUMBER),
                    "rank",
                    field(PolicyType.Scalar.NUMBER)
                )
            );
        }
        return new EnvironmentSchema("benchmark." + scope.toLowerCase(), roots.build());
    }

    private static EnvironmentSchema.Field field(PolicyType type) {
        return new EnvironmentSchema.Field(type, false, true, true);
    }

    private static EnvironmentSchema.Field dynamicString() {
        return new EnvironmentSchema.Field(PolicyType.Scalar.STRING, false, true, true, PolicyType.Scalar.STRING);
    }

    private static final class MapBuilder {

        private final Map<String, EnvironmentSchema.Root> roots = new HashMap<>();

        private void add(String name, Map<String, EnvironmentSchema.Field> fields) {
            roots.put(name, new EnvironmentSchema.Root(field(PolicyType.Scalar.STRING), fields));
        }

        private Map<String, EnvironmentSchema.Root> build() {
            return Map.copyOf(roots);
        }
    }
}
