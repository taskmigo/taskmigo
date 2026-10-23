package io.taskmigo.web.api.support.query;

import io.swagger.v3.oas.annotations.media.Schema;
import io.taskmigo.query.ListQueryRequest;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Binds the public HTTP query parameters for filtering and sorting list endpoints.
public final class ListQueryOptions {

    @Schema(description = "Filter expression using the Taskmigo FilterBy DSL.")
    private @Nullable String filterBy;

    @Schema(
        description = "Sort fields in priority order. Prefix a field with '-' for descending order; ascending is the default. Repeat the parameter for multiple fields."
    )
    private List<String> sortBy = List.of();

    public @Nullable String getFilterBy() {
        return this.filterBy;
    }

    public List<String> getSortBy() {
        return this.sortBy;
    }

    public void setFilterBy(@Nullable String filterBy) {
        this.filterBy = filterBy;
    }

    public void setSortBy(@Nullable List<String> sortBy) {
        this.sortBy = sortBy == null ? List.of() : List.copyOf(sortBy);
    }

    /// Converts HTTP-bound values into transport-neutral list-query intent.
    public ListQueryRequest toRequest() {
        return new ListQueryRequest(this.filterBy, this.sortBy);
    }
}
