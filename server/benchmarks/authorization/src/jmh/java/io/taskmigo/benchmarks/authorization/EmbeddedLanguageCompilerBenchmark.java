package io.taskmigo.benchmarks.authorization;

import static org.openjdk.jmh.annotations.Scope.Thread;

import io.taskmigo.language.CompilationProfile;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.CompilerEnvironment;
import io.taskmigo.language.Field;
import io.taskmigo.language.FieldId;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceType;
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
        private String complexity = "SIMPLE";

        @Param({ "PROGRAM", "EXPRESSION" })
        private String compilationMode = "PROGRAM";

        @Param({ "1000" })
        private String statementCount = "1000";

        private final LanguageCompiler compiler = new LanguageCompiler();
        private final CompilerEnvironment schema = schema();
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

    private static CompilerEnvironment schema() {
        MapBuilder roots = new MapBuilder();
        roots.add(
            "principal",
            Map.of(
                "id",
                LanguageType.Scalar.STRING,
                "username",
                LanguageType.Scalar.STRING,
                "role",
                LanguageType.Scalar.STRING,
                "tenantId",
                LanguageType.Scalar.STRING,
                "teamId",
                LanguageType.Scalar.STRING,
                "kind",
                LanguageType.Scalar.STRING,
                "active",
                LanguageType.Scalar.BOOL,
                "level",
                LanguageType.Scalar.NUMBER,
                "rank",
                LanguageType.Scalar.NUMBER,
                "version",
                LanguageType.Scalar.NUMBER
            )
        );
        roots.add(
            "request",
            Map.of(
                "method",
                LanguageType.Scalar.STRING,
                "path",
                LanguageType.Scalar.STRING,
                "pathVariables.userId",
                LanguageType.Scalar.STRING,
                "version",
                LanguageType.Scalar.NUMBER,
                "sequence",
                LanguageType.Scalar.NUMBER
            )
        );
        roots.add(
            "object",
            Map.of(
                "ownerId",
                LanguageType.Scalar.STRING,
                "status",
                LanguageType.Scalar.STRING,
                "kind",
                LanguageType.Scalar.STRING,
                "tenantId",
                LanguageType.Scalar.STRING,
                "visibility",
                LanguageType.Scalar.STRING,
                "enabled",
                LanguageType.Scalar.BOOL,
                "score",
                LanguageType.Scalar.NUMBER,
                "version",
                LanguageType.Scalar.NUMBER,
                "priority",
                LanguageType.Scalar.NUMBER,
                "rank",
                LanguageType.Scalar.NUMBER
            )
        );
        return CompilerEnvironment.of(roots.build());
    }

    private static Field field(String owner, String path, LanguageType type) {
        return new Field(new FieldId(owner + "." + path), FieldPath.parse(path), type, false);
    }

    private static ResourceSchema resource(String name, Map<String, LanguageType> fields) {
        return ResourceSchema.of(
            new ResourceType("benchmark." + name),
            fields
                .entrySet()
                .stream()
                .map(entry -> field(name, entry.getKey(), entry.getValue()))
                .toList()
        );
    }

    private static final class MapBuilder {

        private final Map<String, CompilerEnvironment.Root> roots = new HashMap<>();

        private void add(String name, Map<String, LanguageType> fields) {
            ResourceSchema schema = resource(name, fields);
            this.roots.put(name, new CompilerEnvironment.Root(schema, true));
        }

        private Map<String, CompilerEnvironment.Root> build() {
            return Map.copyOf(this.roots);
        }
    }
}
