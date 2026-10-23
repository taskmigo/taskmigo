package io.taskmigo.ticket.support;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QuerySchemaView;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration(proxyBeanMethods = false)
public class TestTicketAuthorization implements ObjectAuthorization {

    public static AuthorizationContext context(ObjectAuthorizationPredicate<?> predicate) {
        return new PredicateContext(predicate);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Q> ObjectAuthorizationPredicate<Q> authorize(
        AuthorizationContext context,
        QuerySchemaView schema,
        QueryFieldContext queryContext
    ) {
        if (!(context instanceof PredicateContext predicateContext)) {
            throw new IllegalArgumentException("Unsupported test authorization context");
        }
        return (ObjectAuthorizationPredicate<Q>) predicateContext.predicate();
    }

    @Override
    public void validatePolicy(String policy, String method, String path) {}

    private record PredicateContext(ObjectAuthorizationPredicate<?> predicate) implements AuthorizationContext {}
}
