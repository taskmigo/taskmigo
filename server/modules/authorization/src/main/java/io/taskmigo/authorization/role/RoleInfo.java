package io.taskmigo.authorization.role;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Exposes a role and its direct role-hierarchy children to application consumers.
@NullMarked
public record RoleInfo(
    UUID id,
    String name,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String description,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<RoleInfo> children
) {}
