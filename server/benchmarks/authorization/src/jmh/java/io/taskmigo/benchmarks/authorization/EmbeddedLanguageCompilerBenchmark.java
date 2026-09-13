package io.taskmigo.benchmarks.authorization;

import static org.openjdk.jmh.annotations.Scope.Thread;

import io.taskmigo.language.CompilationProfile;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.EnvironmentSchema;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/// Benchmarks compilation across simple and complex program and expression source corpora.
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class EmbeddedLanguageCompilerBenchmark {

    /// Measures compiling one deterministic batch of generated benchmark sources.
    @Benchmark
    public void compileBatch(BenchmarkState state, Blackhole blackhole) {
        List<CompiledSource> compiled = new ArrayList<>(state.cases.size());
        for (LanguageBenchmarkCorpus.BenchmarkCase benchmarkCase : state.cases) {
            try {
                compiled.add(state.compiler.compile(benchmarkCase.source(), state.schema, state.profile));
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                    "Failed benchmark case " + benchmarkCase.id() + ":\n" + benchmarkCase.source(),
                    exception
                );
            }
        }
        blackhole.consume(compiled);
    }

    /// Holds the immutable compiler, schema, profile, and source corpus used by each benchmark thread.
    @State(Thread)
    public static class BenchmarkState {

        @Param({ "SIMPLE", "COMPLEX" })
        @SuppressWarnings({ "CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal" })
        private String complexity = "SIMPLE";

        @Param({ "PROGRAM", "EXPRESSION" })
        @SuppressWarnings({ "CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal" })
        private String compilationMode = "PROGRAM";

        @Param({ "1000" })
        @SuppressWarnings({ "CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal" })
        private String statementCount = "1000";

        private final LanguageCompiler compiler = new LanguageCompiler();
        private final EnvironmentSchema schema = schema();
        private CompilationProfile profile = CompilationProfile.program();
        private List<LanguageBenchmarkCorpus.BenchmarkCase> cases = List.of();

        /// Generates and validates the selected deterministic corpus before JMH starts measuring compilation.
        @Setup
        public void setUp() {
            this.profile = profile(this.compilationMode);
            this.cases = LanguageBenchmarkCorpus.cases(
                this.complexity,
                this.compilationMode,
                Integer.parseInt(this.statementCount)
            );
        }
    }

    private static CompilationProfile profile(String mode) {
        return switch (mode) {
            case "PROGRAM" -> CompilationProfile.program();
            case "EXPRESSION" -> CompilationProfile.expression();
            default -> throw new IllegalArgumentException("Unknown compilation mode: " + mode);
        };
    }

    private static EnvironmentSchema schema() {
        MapBuilder roots = new MapBuilder();
        roots.add(
            "principal",
            Map.of(
                "id",
                field(LanguageType.Scalar.STRING),
                "username",
                field(LanguageType.Scalar.STRING),
                "role",
                field(LanguageType.Scalar.STRING),
                "tenantId",
                field(LanguageType.Scalar.STRING),
                "teamId",
                field(LanguageType.Scalar.STRING),
                "kind",
                field(LanguageType.Scalar.STRING),
                "active",
                field(LanguageType.Scalar.BOOL),
                "level",
                field(LanguageType.Scalar.NUMBER),
                "rank",
                field(LanguageType.Scalar.NUMBER),
                "version",
                field(LanguageType.Scalar.NUMBER)
            )
        );
        roots.add(
            "request",
            Map.of(
                "method",
                field(LanguageType.Scalar.STRING),
                "path",
                field(LanguageType.Scalar.STRING),
                "pathVariables",
                dynamicString(),
                "version",
                field(LanguageType.Scalar.NUMBER),
                "sequence",
                field(LanguageType.Scalar.NUMBER)
            )
        );
        roots.add(
            "object",
            Map.of(
                "ownerId",
                field(LanguageType.Scalar.STRING),
                "status",
                field(LanguageType.Scalar.STRING),
                "kind",
                field(LanguageType.Scalar.STRING),
                "tenantId",
                field(LanguageType.Scalar.STRING),
                "visibility",
                field(LanguageType.Scalar.STRING),
                "enabled",
                field(LanguageType.Scalar.BOOL),
                "score",
                field(LanguageType.Scalar.NUMBER),
                "version",
                field(LanguageType.Scalar.NUMBER),
                "priority",
                field(LanguageType.Scalar.NUMBER),
                "rank",
                field(LanguageType.Scalar.NUMBER)
            )
        );
        return new EnvironmentSchema("benchmark.authorization", roots.build());
    }

    private static EnvironmentSchema.Field field(LanguageType type) {
        return new EnvironmentSchema.Field(type, false, true);
    }

    private static EnvironmentSchema.Field dynamicString() {
        return new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, true, LanguageType.Scalar.STRING);
    }

    private static final class MapBuilder {

        private final Map<String, EnvironmentSchema.Root> roots = new HashMap<>();

        private void add(String name, Map<String, EnvironmentSchema.Field> fields) {
            this.roots.put(name, new EnvironmentSchema.Root(field(LanguageType.Scalar.STRING), fields));
        }

        private Map<String, EnvironmentSchema.Root> build() {
            return Map.copyOf(this.roots);
        }
    }
}
