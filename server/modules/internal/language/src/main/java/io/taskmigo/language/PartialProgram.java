package io.taskmigo.language;

import io.taskmigo.language.ast.ExpressionVisitor;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Represents either a concrete partially evaluated value or a residual typed Language expression.
public sealed interface PartialProgram permits PartialProgram.Concrete, PartialProgram.Residual {
    /// Returns the static program result type represented by this result.
    LanguageType type();

    /// Returns whether partial evaluation produced a concrete value.
    default boolean isConcrete() {
        return this instanceof Concrete;
    }

    /// Holds a concrete value, including a concrete null value.
    record Concrete(@Nullable Object value, LanguageType type) implements PartialProgram {
        public Concrete {
            Objects.requireNonNull(type);
        }
    }

    /// Holds a residual expression without exposing concrete Semantic AST nodes.
    final class Residual implements PartialProgram {

        private final SemanticAst.Expression expression;

        Residual(SemanticAst.Expression expression) {
            this.expression = Objects.requireNonNull(expression);
        }

        /// Translates the residual expression through the stable read-only AST visitor.
        public <R> R map(ExpressionVisitor<R> visitor) {
            return SemanticExpressionMapper.map(this.expression, Objects.requireNonNull(visitor));
        }

        @Override
        public LanguageType type() {
            return this.expression.type();
        }

        SemanticAst.Expression internalExpression() {
            return this.expression;
        }
    }
}
