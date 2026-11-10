package io.taskmigo.authorization.object.application.service;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.application.api.AuthorizationGateway;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationPrincipal;
import io.taskmigo.authorization.request.AuthorizationRequest;
import io.taskmigo.authorization.request.RequestAuthorizationResult;
import io.taskmigo.authorization.request.application.api.RequestAuthorization;
import io.taskmigo.query.QueryFieldContext;
import java.util.Objects;

/// Framework-neutral facade for callers that must perform Request and Object Authorization explicitly.
public final class DefaultAuthorizationGateway implements AuthorizationGateway {

    private final RequestAuthorization requestAuthorization;
    private final ObjectAuthorization objectAuthorization;

    public DefaultAuthorizationGateway(
        RequestAuthorization requestAuthorization,
        ObjectAuthorization objectAuthorization
    ) {
        this.requestAuthorization = Objects.requireNonNull(requestAuthorization);
        this.objectAuthorization = Objects.requireNonNull(objectAuthorization);
    }

    @Override
    public <Q> ObjectAuthorizationPredicate<Q> authorize(
        AuthorizationPrincipal principal,
        AuthorizationRequest request,
        ObjectAuthorizationSchema<Q> schema,
        QueryFieldContext context
    ) {
        Objects.requireNonNull(principal);
        Objects.requireNonNull(request);
        Objects.requireNonNull(schema);
        Objects.requireNonNull(context);
        RequestAuthorizationResult result = this.requestAuthorization.authorize(principal, request);
        if (!result.granted()) {
            throw new AuthorizationException("Request authorization denied");
        }
        return this.objectAuthorization.authorize(result.context(), schema, context);
    }
}
