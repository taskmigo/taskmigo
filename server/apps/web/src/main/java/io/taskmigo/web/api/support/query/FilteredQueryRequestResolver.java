package io.taskmigo.web.api.support.query;

import io.taskmigo.query.FilteredQuery;
import java.util.Objects;
import org.springframework.stereotype.Component;

/// Resolves public HTTP query parameters against the Query operation declared by the current handler.
@Component
public final class FilteredQueryRequestResolver {

    private final FilteredQueryRequestCompiler compiler;
    private final QueryRequestContextResolver contexts;

    public FilteredQueryRequestResolver(FilteredQueryRequestCompiler compiler, QueryRequestContextResolver contexts) {
        this.compiler = Objects.requireNonNull(compiler);
        this.contexts = Objects.requireNonNull(contexts);
    }

    public <Q> FilteredQuery<Q> resolve(FilteredQueryRequest request) {
        ResolvedQueryRequestContext context = this.contexts.resolve();
        return this.compiler.compile(context.schema().operation(), request, context.queryContext());
    }
}
