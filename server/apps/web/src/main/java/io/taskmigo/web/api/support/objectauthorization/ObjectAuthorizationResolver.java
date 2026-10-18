package io.taskmigo.web.api.support.objectauthorization;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.web.api.support.query.QueryRequestContextResolver;
import io.taskmigo.web.api.support.query.ResolvedQueryRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.stereotype.Component;

/// Resolves Object Authorization from server-owned request security state for the current Query operation.
@Component
public final class ObjectAuthorizationResolver {

    private final ObjectAuthorization authorization;
    private final QueryRequestContextResolver queryContexts;
    private final HttpServletRequest request;

    public ObjectAuthorizationResolver(
        ObjectAuthorization authorization,
        QueryRequestContextResolver queryContexts,
        HttpServletRequest request
    ) {
        this.authorization = Objects.requireNonNull(authorization);
        this.queryContexts = Objects.requireNonNull(queryContexts);
        this.request = Objects.requireNonNull(request);
    }

    public <Q> ResolvedObjectAuthorization<Q> resolve() {
        ResolvedQueryRequestContext queryContext = this.queryContexts.resolve();
        Object value = this.request.getAttribute(AuthorizationContext.ATTRIBUTE);
        if (!(value instanceof AuthorizationContext context)) {
            throw new IllegalStateException("authorization context is missing for object access");
        }
        ObjectAuthorizationPredicate<Q> predicate = this.authorization.authorize(
            context,
            queryContext.schema(),
            queryContext.queryContext()
        );
        return new ResolvedObjectAuthorization<>(predicate, queryContext.queryContext());
    }
}
