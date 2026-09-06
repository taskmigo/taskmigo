package io.taskmigo.embeddedlanguage;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Holds either a concrete boolean or a typed residual program expression.
@SuppressWarnings("checkstyle:NeedBraces")
public record PartialProgram(@Nullable Boolean value, LanguageIr.@Nullable Expression residual) {
    public PartialProgram(@Nullable Boolean value, LanguageIr.@Nullable Expression residual) {
        this.value = value;
        this.residual = residual;
        if ((value == null) == (residual == null)) throw new IllegalArgumentException(
            "partial program must have one result"
        );
        if (residual != null) Objects.requireNonNull(residual);
    }

    /// Returns whether partial evaluation produced a concrete result.
    public boolean isConcrete() {
        return this.value != null;
    }
}
