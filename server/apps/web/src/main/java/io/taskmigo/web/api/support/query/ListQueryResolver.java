package io.taskmigo.web.api.support.query;

import io.taskmigo.query.ListQuerySpec;
import io.taskmigo.query.QueryContract;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QuerySchemaView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/// Resolves public HTTP query parameters against the Query Contract declared by the current handler.
@Component
public final class ListQueryResolver {

    private final ListQueryOptionsCompiler compiler;
    private final List<QuerySchemaView> schemas;
    private final QueryFieldContextResolver contexts;
    private final HttpServletRequest request;

    public ListQueryResolver(
        ListQueryOptionsCompiler compiler,
        List<QuerySchemaView> schemas,
        QueryFieldContextResolver contexts,
        HttpServletRequest request
    ) {
        this.compiler = Objects.requireNonNull(compiler);
        this.schemas = List.copyOf(Objects.requireNonNull(schemas));
        this.contexts = Objects.requireNonNull(contexts);
        this.request = Objects.requireNonNull(request);
    }

    public <Q> ListQuerySpec<Q> resolve(ListQueryOptions requestOptions) {
        Object handler = this.request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod method)) {
            throw new IllegalStateException("current request is not mapped to a handler method");
        }
        QueryContract contract = method.getMethodAnnotation(QueryContract.class);
        if (contract == null) {
            throw new IllegalStateException("query handler must declare @QueryContract");
        }
        QuerySchemaView schema = this.schema(contract.value());
        QueryFieldContext context = this.contexts.resolve(contract.value(), new ServletWebRequest(this.request));
        return this.compiler.compile(schema, requestOptions, context);
    }

    private QuerySchemaView schema(Class<? extends QuerySchemaView> contract) {
        List<QuerySchemaView> matches = this.schemas.stream().filter(contract::isInstance).toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                "Expected exactly one Query Schema for contract " + contract.getName() + ", found " + matches.size()
            );
        }
        return matches.getFirst();
    }
}
