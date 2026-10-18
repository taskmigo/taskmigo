package io.taskmigo.web.api.support.query;

import io.taskmigo.query.QueryFieldContext;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;

/// Resolves and caches one immutable runtime Query field context per request operation.
@Component
public final class QueryFieldContextResolver {

    private static final String ATTRIBUTE_PREFIX = QueryFieldContextResolver.class.getName() + ".";

    private final List<QueryFieldContextSource> sources;

    public QueryFieldContextResolver(List<QueryFieldContextSource> sources) {
        this.sources = List.copyOf(Objects.requireNonNull(sources));
    }

    public QueryFieldContext resolve(String operation, NativeWebRequest request) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(request);
        String attribute = ATTRIBUTE_PREFIX + operation;
        Object cached = request.getAttribute(attribute, RequestAttributes.SCOPE_REQUEST);
        if (cached instanceof QueryFieldContext context) {
            return context;
        }

        List<QueryFieldContextSource> matches = this.sources
            .stream()
            .filter(source -> source.supports(operation))
            .toList();
        if (matches.size() > 1) {
            throw new IllegalStateException(
                "Expected at most one runtime Query context source for " + operation + ", found multiple"
            );
        }

        QueryFieldContext context = matches.isEmpty()
            ? QueryFieldContext.empty()
            : Objects.requireNonNull(matches.getFirst().resolve(operation, request));
        request.setAttribute(attribute, context, RequestAttributes.SCOPE_REQUEST);
        return context;
    }
}
