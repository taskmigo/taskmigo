package io.taskmigo.language;

import io.taskmigo.language.ast.ExpressionVisitor;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/// Represents one immutable, statically checked Language source ready for repeated execution.
public final class CompiledSource {

    private static final EmbeddedLanguageEvaluator EVALUATOR = new EmbeddedLanguageEvaluator();
    private static final EmbeddedLanguagePartialEvaluator PARTIAL_EVALUATOR = new EmbeddedLanguagePartialEvaluator();

    private final SemanticAst program;

    CompiledSource(SemanticAst program) {
        this.program = Objects.requireNonNull(program);
    }

    /// Returns the statically determined result type.
    public LanguageType resultType() {
        return this.program.resultType();
    }

    /// Returns whether the result may be null.
    public boolean resultNullable() {
        return this.program.resultNullable();
    }

    /// Translates the compiled expression through the stable read-only AST visitor.
    public <R> R map(ExpressionVisitor<R> visitor) {
        return SemanticExpressionMapper.map(this.program.expression(), Objects.requireNonNull(visitor));
    }

    /// Returns the typed expression for compatibility while consumers migrate to {@link #map(ExpressionVisitor)}.
    public SemanticAst.Expression expression() {
        return this.program.expression();
    }

    /// Returns a constant Boolean result when compilation reduced the source to one.
    public Optional<Boolean> constantBoolean() {
        return this.program.expression() instanceof SemanticAst.Literal literal &&
            literal.value() instanceof Boolean value
            ? Optional.of(value)
            : Optional.empty();
    }

    /// Evaluates this source against approved runtime roots.
    public @Nullable Object evaluate(Map<String, ?> roots) {
        return EVALUATOR.evaluate(this.program, roots);
    }

    /// Partially evaluates this source against the roots currently known to the consumer.
    public PartialProgram partialEvaluate(Map<String, ?> knownRoots) {
        return PARTIAL_EVALUATOR.partial(this.program, knownRoots);
    }

    /// Returns the collision-resistant identity of the exact source content.
    public String sourceFingerprint() {
        return this.program.sourceFingerprint();
    }

    /// Returns the Environment Schema identity fingerprint used for static checking.
    public String schemaFingerprint() {
        return this.program.schemaFingerprint();
    }

    /// Returns the Language/compiler contract identity used for this artifact.
    public String compilerFingerprint() {
        return this.program.compilerFingerprint();
    }

    /// Returns the source entry mode used for compilation.
    public CompilationMode mode() {
        return this.program.mode();
    }

    /// Returns the Compilation Profile identity used for this artifact.
    public String profileFingerprint() {
        return this.program.profileFingerprint();
    }
}
