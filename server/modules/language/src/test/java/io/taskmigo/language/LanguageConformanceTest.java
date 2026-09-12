package io.taskmigo.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LanguageConformanceTest {

    /**
     * Verifies that short-circuit folding also removes runtime-root requirements from dead expressions.
     *
     * Given: a non-symbolic request root referenced only on the dead right-hand side of `false && ...`.
     * Expect: compilation produces no required root and evaluation succeeds without supplying request.
     */
    @Test
    @DisplayName("derives required roots from the final folded expression")
    void shouldDropRequiredRootWhenShortCircuitFoldingRemovesReference() {
        // Arrange
        EnvironmentSchema schema = new EnvironmentSchema(
            "folded-roots",
            Map.of(
                "request",
                new EnvironmentSchema.Root(
                    new EnvironmentSchema.Field(LanguageType.Scalar.BOOL, false, false),
                    Map.of("flag", new EnvironmentSchema.Field(LanguageType.Scalar.BOOL, false, false))
                )
            )
        );

        // Act
        SemanticAst program = new EmbeddedLanguageCompiler().compile(
            "false && request.flag",
            schema,
            CompilationProfile.expression()
        );

        // Assert
        assertThat(program.requiredRoots()).isEmpty();
        assertThat(new EmbeddedLanguageEvaluator().evaluate(program, Map.of())).isEqualTo(false);
    }

    /**
     * Verifies the dependency representation beyond the single-long fast path.
     *
     * Given: an Environment Schema with seventy roots and an expression using roots on both sides of the 64-root boundary.
     * Expect: dependency metadata contains both roots and evaluation resolves both optimized root slots.
     */
    @Test
    @DisplayName("supports dependency metadata beyond sixty four roots")
    void shouldTrackDependenciesWhenSchemaContainsMoreThanSixtyFourRoots() {
        // Arrange
        Map<String, EnvironmentSchema.Root> roots = new HashMap<>();
        for (int index = 0; index < 70; index++) {
            roots.put(
                "root" + index,
                new EnvironmentSchema.Root(
                    new EnvironmentSchema.Field(LanguageType.Scalar.NUMBER, false, false),
                    Map.of()
                )
            );
        }
        EnvironmentSchema schema = new EnvironmentSchema("wide-roots", roots);

        // Act
        SemanticAst program = new EmbeddedLanguageCompiler().compile(
            "root0 == root69",
            schema,
            CompilationProfile.expression()
        );
        Object result = new EmbeddedLanguageEvaluator().evaluate(program, Map.of("root0", 7, "root69", 7));

        // Assert
        assertThat(program.expression().dependencies()).containsExactlyInAnyOrder("root0", "root69");
        assertThat(result).isEqualTo(true);
    }

    /**
     * Verifies that immutable literal-list storage preserves valid null language values.
     *
     * Given: a homogeneous list literal containing null.
     * Expect: compilation and evaluation preserve the null element without relying on Java's null-rejecting List.copyOf.
     */
    @Test
    @DisplayName("preserves null values in language list literals")
    void shouldPreserveNullWhenListLiteralContainsNull() {
        // Arrange
        EnvironmentSchema schema = scalarSchema("unused", LanguageType.Scalar.STRING);

        // Act
        Object result = new LanguageCompiler()
            .compile("[null]", schema, CompilationProfile.expression())
            .evaluate(Map.of());

        // Assert
        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>) result)
            .hasSize(1)
            .containsExactly((Object) null);
    }

    /**
     * Verifies that aliases of structured roots retain their static shape and optimized root slot.
     *
     * Given: a structured object root assigned to a lexical alias.
     * Expect: a field reference through the alias resolves and evaluates exactly like the original root path.
     */
    @Test
    @DisplayName("resolves structured root fields through lexical aliases")
    void shouldResolveStructuredFieldWhenRootIsAssignedToLocalAlias() {
        // Arrange
        EnvironmentSchema.Field name = new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false);
        Map<String, EnvironmentSchema.Field> fields = Map.of("name", name);
        EnvironmentSchema schema = new EnvironmentSchema(
            "structured-alias",
            Map.of(
                "object",
                new EnvironmentSchema.Root(
                    new EnvironmentSchema.Field(new LanguageType.StructuredType("Object", fields), false, false),
                    fields
                )
            )
        );
        CompiledSource source = new LanguageCompiler().compile("const alias = object; return alias.name;", schema);

        // Act
        Object result = source.evaluate(Map.of("object", Map.of("name", "Ada")));

        // Assert
        assertThat(result).isEqualTo("Ada");
    }

    /**
     * Verifies the exact-source identity contract while retaining SHA-256 strength.
     *
     * Given: two sources that differ only by one literal.
     * Expect: each source has a 256-bit lowercase-hex fingerprint and the identities differ.
     */
    @Test
    @DisplayName("uses collision resistant exact source identities")
    void shouldChangeSourceFingerprintWhenExactSourceChanges() {
        // Arrange
        LanguageCompiler compiler = new LanguageCompiler();
        EnvironmentSchema schema = scalarSchema("unused", LanguageType.Scalar.STRING);

        // Act
        CompiledSource first = compiler.compile("1 + 2", schema, CompilationProfile.expression());
        CompiledSource second = compiler.compile("1 + 3", schema, CompilationProfile.expression());

        // Assert
        assertThat(first.sourceFingerprint()).matches("[0-9a-f]{64}");
        assertThat(second.sourceFingerprint()).matches("[0-9a-f]{64}");
        assertThat(second.sourceFingerprint()).isNotEqualTo(first.sourceFingerprint());
    }

    /**
     * Verifies that bounded token and syntax-depth limits remain enforced by the single-pass frontend accounting.
     *
     * Given: deliberately small token and syntax-depth limits.
     * Expect: sources exceeding either limit fail closed with ComplexityError.
     */
    @Test
    @DisplayName("enforces token and syntax depth limits during direct compilation")
    void shouldRejectSourcesWhenTokenOrSyntaxDepthLimitIsExceeded() {
        // Arrange
        EnvironmentSchema schema = scalarSchema("unused", LanguageType.Scalar.STRING);
        EmbeddedLanguageCompiler tokenLimited = new EmbeddedLanguageCompiler(
            new CompilerLimits(100, 2, 20, 20, 20, 20, 20, 20)
        );
        EmbeddedLanguageCompiler depthLimited = new EmbeddedLanguageCompiler(
            new CompilerLimits(100, 20, 2, 20, 20, 20, 20, 20)
        );

        // Act + Assert
        assertThatThrownBy(() -> tokenLimited.compile("return true;", schema))
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.ComplexityError);
        assertThatThrownBy(() -> depthLimited.compile("(((true)))", schema, CompilationProfile.expression()))
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.ComplexityError);
    }

    private static EnvironmentSchema scalarSchema(String root, LanguageType type) {
        return new EnvironmentSchema(
            "conformance-" + root + '-' + type,
            Map.of(root, new EnvironmentSchema.Root(new EnvironmentSchema.Field(type, false, false), Map.of()))
        );
    }
}
