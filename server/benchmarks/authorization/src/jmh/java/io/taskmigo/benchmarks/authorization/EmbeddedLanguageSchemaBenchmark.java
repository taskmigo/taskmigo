package io.taskmigo.benchmarks.authorization;

import static org.openjdk.jmh.annotations.Scope.Thread;

import io.taskmigo.language.EnvironmentSchema;
import io.taskmigo.language.LanguageType;
import java.util.LinkedHashMap;
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

/// Benchmarks immutable Environment Schema indexing and fingerprint construction.
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class EmbeddedLanguageSchemaBenchmark {

    /// Measures construction of a consumer-owned schema including trie indexing and one deterministic fingerprint.
    @Benchmark
    public void buildEnvironmentSchema(SchemaState state, Blackhole blackhole) {
        blackhole.consume(new EnvironmentSchema("benchmark.schema", state.roots));
    }

    /// Holds reusable schema declarations while construction remains inside the measured operation.
    @State(Thread)
    public static class SchemaState {

        @Param({ "10", "1000" })
        @SuppressWarnings({ "CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal" })
        private String fieldCount = "10";

        private Map<String, EnvironmentSchema.Root> roots = Map.of();

        /// Builds a flat but wide root declaration before each benchmark trial.
        @Setup
        public void setUp() {
            int count = Integer.parseInt(this.fieldCount);
            Map<String, EnvironmentSchema.Field> fields = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                fields.put("field" + index, new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false));
            }
            this.roots = Map.of(
                "record",
                new EnvironmentSchema.Root(
                    new EnvironmentSchema.Field(
                        new LanguageType.StructuredType("BenchmarkRecord", fields),
                        false,
                        false
                    ),
                    fields
                )
            );
        }
    }
}
