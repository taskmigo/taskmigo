package io.taskmigo.web.api.support.objectauthorization;

import io.taskmigo.authorization.object.AuthorizationContract;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.web.api.support.query.QueryFieldContextResolver;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/// Resolves server-owned Object Authorization predicates from the handler's explicit Authorization Contract.
@Component
public final class ObjectAuthorizationPredicateArgumentResolver implements HandlerMethodArgumentResolver {

    private final ObjectAuthorization authorization;
    private final List<ObjectAuthorizationSchema<?>> schemas;
    private final QueryFieldContextResolver contexts;

    public ObjectAuthorizationPredicateArgumentResolver(
        ObjectAuthorization authorization,
        List<ObjectAuthorizationSchema<?>> schemas,
        QueryFieldContextResolver contexts
    ) {
        this.authorization = authorization;
        this.schemas = List.copyOf(schemas);
        this.contexts = contexts;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == ObjectAuthorizationPredicate.class;
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        @Nullable ModelAndViewContainer container,
        NativeWebRequest webRequest,
        @Nullable WebDataBinderFactory binderFactory
    ) {
        AuthorizationContract contract = parameter.getMethodAnnotation(AuthorizationContract.class);
        if (contract == null) {
            throw new IllegalStateException("ObjectAuthorizationPredicate handler must declare @AuthorizationContract");
        }
        ObjectAuthorizationSchema<?> schema = this.schema(contract.value());
        QueryFieldContext queryContext = this.contexts.resolve(contract.value(), webRequest);
        Object value = webRequest.getAttribute(AuthorizationContext.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof AuthorizationContext context)) {
            throw new IllegalStateException("authorization context is missing for object access");
        }
        return authorize(this.authorization, context, schema, queryContext);
    }

    private ObjectAuthorizationSchema<?> schema(Class<? extends ObjectAuthorizationSchema<?>> contract) {
        List<ObjectAuthorizationSchema<?>> matches = this.schemas.stream().filter(contract::isInstance).toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                "Expected exactly one Object Authorization schema for contract " +
                    contract.getName() +
                    ", found " +
                    matches.size()
            );
        }
        return matches.getFirst();
    }

    private static <Q> ObjectAuthorizationPredicate<Q> authorize(
        ObjectAuthorization authorization,
        AuthorizationContext context,
        ObjectAuthorizationSchema<Q> schema,
        QueryFieldContext queryContext
    ) {
        return authorization.authorize(context, schema, queryContext);
    }
}
