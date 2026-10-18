package io.taskmigo.web.api.support.query;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/// Binds the public HTTP query parameters for filtering and sorting list endpoints.
public final class FilteredQueryRequest {

    @Schema(description = "Filter expression using the Taskmigo FilterBy DSL.")
    private @Nullable String filterBy;

    @Schema(description = "Field path to sort by.")
    private @Nullable String sortBy;

    @Schema(
        description = "Sort direction. Requires sortBy; defaults to ASC when sortBy is present.",
        allowableValues = { "ASC", "DESC" }
    )
    private @Nullable String sortOrder;

    public @Nullable String getFilterBy() {
        return this.filterBy;
    }

    public @Nullable String getSortBy() {
        return this.sortBy;
    }

    public @Nullable String getSortOrder() {
        return this.sortOrder;
    }

    public void setFilterBy(@Nullable String filterBy) {
        this.filterBy = filterBy;
    }

    public void setSortBy(@Nullable String sortBy) {
        this.sortBy = sortBy;
    }

    public void setSortOrder(@Nullable String sortOrder) {
        this.sortOrder = sortOrder;
    }
}
