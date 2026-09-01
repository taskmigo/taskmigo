package io.taskmigo.web.api.v0.infrastructure.pagination;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/// Binds and validates the shared query parameters for offset-paginated API endpoints.
///
/// Defaults preserve the v0 pagination contract when clients omit either parameter.
public final class OffsetPageRequest {

    @Min(1)
    private int page = 1;

    @Min(1)
    @Max(100)
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
