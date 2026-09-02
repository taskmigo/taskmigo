package io.taskmigo.foundation;

import java.util.List;

/// Represents one offset-paginated page independently of a transport response format.
///
/// Offset and cursor pagination intentionally use separate result types because their navigation contracts differ.
///
/// @param items the items in the requested page
/// @param totalItems the total number of available items
/// @param totalPages the number of pages available at the requested page size
public record OffsetPage<T>(List<T> items, long totalItems, long totalPages) {}
