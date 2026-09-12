package io.taskmigo.benchmarks.authorization;

import static org.openjdk.jmh.annotations.Scope.Thread;

import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.EnvironmentSchema;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageType;
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
        @SuppressWarnings({ "CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal" })
        private String listSize = "10";

        private @Nullable CompiledSource compiled;
        private Map<String, ?> roots = Map.of();
        private Map<String, ?> sparseRoots = Map.of();

        /// Builds the schema, compiled source, and bounded list before measurement begins.
        @Setup
        public void setUp() {
            int size = Integer.parseInt(this.listSize);
            EnvironmentSchema schema = new EnvironmentSchema(
                "benchmark.runtime",
                Map.of(
                    "record",
                    new EnvironmentSchema.Root(
                        field(LanguageType.Scalar.STRING, false),
                        Map.of("values", field(new LanguageType.ListType(LanguageType.Scalar.NUMBER), true))
                    ),
                    "threshold",
                    new EnvironmentSchema.Root(field(LanguageType.Scalar.NUMBER, false), Map.of())
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

        private static EnvironmentSchema.Field field(LanguageType type, boolean symbolic) {
            return new EnvironmentSchema.Field(type, false, symbolic);
        }
    }
}
