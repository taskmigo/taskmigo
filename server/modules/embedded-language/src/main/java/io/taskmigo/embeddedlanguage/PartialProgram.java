package io.taskmigo.embeddedlanguage;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Represents either a concrete partially evaluated value or a residual typed Semantic AST expression.
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

    /// Holds a residual Semantic AST expression.
    record Residual(SemanticAst.Expression expression) implements PartialProgram {
        public Residual {
            Objects.requireNonNull(expression);
        }

        @Override
        public LanguageType type() {
            return this.expression.type();
        }
    }
}
