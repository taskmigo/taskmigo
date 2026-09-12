package io.taskmigo.internal.security;

import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.authorization.request.AuthorizationPrincipal;
import io.taskmigo.authorization.request.AuthorizationRequest;
import io.taskmigo.authorization.request.RequestAuthorization;
import io.taskmigo.authorization.request.RequestAuthorizationResult;
import io.taskmigo.identity.user.UserException;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/// Applies effective request Statements before a versioned API controller is invoked.
@Component
final class RequestAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestAuthorizationManager.class);

    private static final String DECISION_ATTRIBUTE = "taskmigo.authorization.request.decision";

    private final RequestAuthorization authorization;

    RequestAuthorizationManager(RequestAuthorization authorization) {
        this.authorization = authorization;
    }

    @Override
    public AuthorizationDecision authorize(
        Supplier<? extends @Nullable Authentication> authentication,
        RequestAuthorizationContext context
    ) {
        Authentication current = authentication.get();
        if (current == null) {
            return new AuthorizationDecision(false);
        }
        if (!(current instanceof JwtAuthenticationToken token)) {
            return new AuthorizationDecision(false);
        }
        if (!hasAuthority(current, "SCOPE_taskmigo.api")) {
            return new AuthorizationDecision(false);
        }

        Jwt jwt = token.getToken();
        String principalType = jwt.getClaimAsString("principal_type");
        if (!"user".equals(principalType) && !"service".equals(principalType)) {
            return new AuthorizationDecision(false);
        }

        String userId = jwt.getClaimAsString("user_id");
        if (userId == null) {
            return new AuthorizationDecision(false);
        }

        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            return new AuthorizationDecision(false);
        }
        try {
            Object cachedDecision = context.getRequest().getAttribute(DECISION_ATTRIBUTE);
            if (cachedDecision instanceof Boolean decision) {
                return new AuthorizationDecision(decision);
            }
            String method = context.getRequest().getMethod();
            String path = context.getRequest().getRequestURI();
            RequestAuthorizationResult result = this.authorization.authorize(
                new AuthorizationPrincipal(id, principalUsername(jwt, current)),
                new AuthorizationRequest(method, path, context.getVariables())
            );
            context.getRequest().setAttribute(AuthorizationContext.ATTRIBUTE, result.context());
            context.getRequest().setAttribute(DECISION_ATTRIBUTE, result.granted());
            return new AuthorizationDecision(result.granted());
        } catch (UserException exception) {
            LOGGER.warn("Request authorization failed closed for principal {}", userId, exception);
            return new AuthorizationDecision(false);
        }
    }

    private static String principalUsername(Jwt jwt, Authentication authentication) {
        String username = jwt.getClaimAsString("principal_username");
        return username == null ? authentication.getName() : username;
    }

    private static boolean hasAuthority(Authentication authentication, String expected) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(expected::equals);
    }
}
