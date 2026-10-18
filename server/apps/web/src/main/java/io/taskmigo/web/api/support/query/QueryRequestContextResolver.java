package io.taskmigo.web.api.support.query;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.query.QuerySchemaView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/// Resolves the current handler's Query operation without exposing server-owned state as controller parameters.
@Component
public final class QueryRequestContextResolver {

    private static final String ATTRIBUTE = QueryRequestContextResolver.class.getName() + ".resolved";

    private final List<QuerySchemaView> schemas;
    private final QueryFieldContextResolver contexts;
    private final HttpServletRequest request;

    public QueryRequestContextResolver(
        List<QuerySchemaView> schemas,
        QueryFieldContextResolver contexts,
        HttpServletRequest request
    ) {
        this.schemas = List.copyOf(Objects.requireNonNull(schemas));
        this.contexts = Objects.requireNonNull(contexts);
        this.request = Objects.requireNonNull(request);
    }

    public ResolvedQueryRequestContext resolve() {
        Object cached = this.request.getAttribute(ATTRIBUTE);
        if (cached instanceof ResolvedQueryRequestContext context) {
            return context;
        }

        Object handler = this.request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod method)) {
            throw new IllegalStateException("current request is not mapped to a handler method");
        }
        QueryOperation operation = method.getMethodAnnotation(QueryOperation.class);
        if (operation == null) {
            throw new IllegalStateException("current handler must declare @QueryOperation");
        }

        List<QuerySchemaView> matches = this.schemas
            .stream()
            .filter(candidate -> candidate.operation().equals(operation.value()))
            .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                "Expected exactly one Query Schema for operation " + operation.value() + ", found " + matches.size()
            );
        }

        QueryFieldContext queryContext = this.contexts.resolve(operation.value(), new ServletWebRequest(this.request));
        ResolvedQueryRequestContext resolved = new ResolvedQueryRequestContext(matches.getFirst(), queryContext);
        this.request.setAttribute(ATTRIBUTE, resolved);
        return resolved;
    }
}
