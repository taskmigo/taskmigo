package io.taskmigo.identity.group;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Exposes a group and its direct group-hierarchy children to application consumers.
public record GroupInfo(
    UUID id,
    String code,
    String displayName,
    @Nullable String description,
    List<GroupInfo> children
) {}
