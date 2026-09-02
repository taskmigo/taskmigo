package io.taskmigo.api.foundation.v0.infrastructure.pagination;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/// Binds and validates the shared query parameters for offset-paginated API endpoints.
///
/// Defaults preserve the v0 pagination contract when clients omit either parameter.
public final class OffsetPageRequest {

    @Min(1)
    @Schema(description = "Page number to retrieve (1-based index)", defaultValue = "1", minimum = "1")
    private int page = 1;

    @Min(1)
    @Max(100)
    @Schema(description = "Number of items per page", defaultValue = "20", minimum = "1", maximum = "100")
    private int pageSize = 20;

    public int page() {
        return this.page;
    }

    public int getPage() {
        return this.page;
    }

    public int pageSize() {
        return this.pageSize;
    }

    public int getPageSize() {
        return this.pageSize;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
