package io.taskmigo.web.security;

import io.taskmigo.auth.authorization.request.RequestAuthorizationService;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
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

    private final RequestAuthorizationService authorization;

    RequestAuthorizationManager(RequestAuthorizationService authorization) {
        this.authorization = authorization;
    }

    @Override
    public AuthorizationDecision authorize(
        Supplier<? extends @Nullable Authentication> authentication,
        RequestAuthorizationContext context
    ) {
        Authentication current = authentication.get();
        if (current == null) return new AuthorizationDecision(false);
        if (!(current instanceof JwtAuthenticationToken token)) return new AuthorizationDecision(false);
        if (!hasAuthority(current, "SCOPE_taskmigo.api")) return new AuthorizationDecision(false);

        Jwt jwt = token.getToken();
        String principalType = jwt.getClaimAsString("principal_type");
        if (!"user".equals(principalType) && !"service".equals(principalType)) return new AuthorizationDecision(false);

        String userId = jwt.getClaimAsString("user_id");
        if (userId == null) return new AuthorizationDecision(false);

        try {
            UUID id = UUID.fromString(userId);
            Map<String, ?> roots = Map.of(
                "principal",
                Map.of("id", userId, "username", principalUsername(jwt, current)),
                "request",
                Map.of(
                    "method",
                    context.getRequest().getMethod(),
                    "path",
                    context.getRequest().getRequestURI().split("\\?", 2)[0]
                )
            );
            return new AuthorizationDecision(
                this.authorization
                    .authorize(
                        id,
                        context.getRequest().getMethod(),
                        context.getRequest().getRequestURI().split("\\?", 2)[0],
                        roots
                    )
                    .allowed()
            );
        } catch (RuntimeException exception) {
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
