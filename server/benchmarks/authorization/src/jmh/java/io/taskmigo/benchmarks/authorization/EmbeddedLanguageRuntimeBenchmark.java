package io.taskmigo.benchmarks.authorization;

import static org.openjdk.jmh.annotations.Scope.Thread;

import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.CompilerEnvironment;
import io.taskmigo.language.Field;
import io.taskmigo.language.FieldId;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/// Benchmarks allocation-sensitive direct and partial evaluation of bounded collection quantifiers.
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class EmbeddedLanguageRuntimeBenchmark {

    /// Measures repeated direct evaluation of one already compiled quantified policy.
    @Benchmark
    public void evaluateQuantifier(RuntimeState state, Blackhole blackhole) {
        blackhole.consume(Objects.requireNonNull(state.compiled).evaluate(state.roots));
    }

    /// Measures repeated partial evaluation when all quantified inputs are known.
    @Benchmark
    public void partialEvaluateQuantifier(RuntimeState state, Blackhole blackhole) {
        blackhole.consume(Objects.requireNonNull(state.compiled).partialEvaluate(state.roots));
    }

    /// Measures partial evaluation when the collection remains symbolic and only scalar context is known.
    @Benchmark
    public void partialEvaluateSparseQuantifier(RuntimeState state, Blackhole blackhole) {
        blackhole.consume(Objects.requireNonNull(state.compiled).partialEvaluate(state.sparseRoots));
    }

    /// Holds the reusable compiled source and immutable runtime values for one benchmark thread.
    @State(Thread)
    public static class RuntimeState {

        @Param({ "10", "1000" })
        private String listSize = "10";

        private @Nullable CompiledSource compiled;
        private Map<String, ?> roots = Map.of();
        private Map<String, ?> sparseRoots = Map.of();

        /// Builds the schema, compiled source, and bounded list before measurement begins.
        @Setup
        public void setUp() {
            int size = Integer.parseInt(this.listSize);
            ResourceSchema record = ResourceSchema.of(
                new ResourceType("benchmark.record"),
                List.of(
                    new Field(
                        new FieldId("record.values"),
                        FieldPath.parse("values"),
                        new LanguageType.ListType(LanguageType.Scalar.NUMBER),
                        false
                    )
                )
            );
            ResourceSchema threshold = ResourceSchema.of(new ResourceType("benchmark.threshold"), List.of());
            CompilerEnvironment schema = CompilerEnvironment.of(
                Map.of(
                    "record",
                    new CompilerEnvironment.Root(
                        record,
                        new LanguageType.StructuredType("Record", Map.of()),
                        false,
                        true
                    ),
                    "threshold",
                    new CompilerEnvironment.Root(threshold, LanguageType.Scalar.NUMBER, false, true)
                )
            );
            this.compiled = new LanguageCompiler().compile(
                "return all(record.values, value => value >= threshold);",
                schema
            );
            List<Integer> values = IntStream.range(0, size).boxed().toList();
            this.roots = Map.of("record", Map.of("values", values), "threshold", 0);
            this.sparseRoots = Map.of("threshold", 0);
        }
    }
}
