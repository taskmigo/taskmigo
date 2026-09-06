package io.taskmigo.policy;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Holds either a concrete boolean or a typed residual policy expression.
@SuppressWarnings("checkstyle:NeedBraces")
public record PartialPolicy(@Nullable Boolean value, PolicyIr.@Nullable Expression residual) {
    public PartialPolicy(@Nullable Boolean value, PolicyIr.@Nullable Expression residual) {
        this.value = value;
        this.residual = residual;
        if ((value == null) == (residual == null)) throw new IllegalArgumentException(
            "partial policy must have one result"
        );
        if (residual != null) Objects.requireNonNull(residual);
    }

    /// Returns whether partial evaluation produced a concrete result.
    public boolean isConcrete() {
        return this.value != null;
    }
}
