package io.taskmigo.auth.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Exposes a group and its direct group-hierarchy children to application consumers.
public record GroupInfo(
    UUID id,
    String name,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String description,
    List<GroupInfo> children
) {}
