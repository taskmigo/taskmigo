package io.taskmigo.eav.value.application;

import java.util.Objects;

/// The validated instance projection and whether applying the patch actually changed stored values.
public record EavInstancePatchResult(EavInstanceView instance, boolean changed) {
    public EavInstancePatchResult {
        Objects.requireNonNull(instance);
    }
}
