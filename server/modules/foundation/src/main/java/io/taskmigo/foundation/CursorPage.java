package io.taskmigo.foundation;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// Represents one cursor-paginated slice while keeping the cursor opaque to callers.
public record CursorPage<T>(List<T> items, @Nullable String nextCursor) {}
