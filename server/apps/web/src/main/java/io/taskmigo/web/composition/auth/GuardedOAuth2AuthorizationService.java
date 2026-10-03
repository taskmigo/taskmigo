package io.taskmigo.web.composition.auth;

import io.taskmigo.identity.user.application.port.in.api.UserSessionLifecycleService;
import io.taskmigo.web.adapter.in.security.session.UserSessionPrincipal;
import java.security.Principal;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/// Prevents interactive grants from recreating OAuth state after their persisted User is deleted.
final class GuardedOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final UserSessionLifecycleService lifecycle;

    GuardedOAuth2AuthorizationService(OAuth2AuthorizationService delegate, UserSessionLifecycleService lifecycle) {
        this.delegate = delegate;
        this.lifecycle = lifecycle;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(authorization.getAuthorizationGrantType())) {
            this.delegate.save(authorization);
            return;
        }
        Object attribute = authorization.getAttribute(Principal.class.getName());
        UserSessionPrincipal principal =
            attribute instanceof Authentication authentication
                ? UserSessionPrincipal.fromAuthentication(authentication)
                : null;
        if (
            principal == null ||
            !authorization.getPrincipalName().equals(principal.getUsername()) ||
            !this.lifecycle.runIfActive(principal.userId(), () -> this.delegate.save(authorization))
        ) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        this.delegate.remove(authorization);
    }

    @Override
    public @Nullable OAuth2Authorization findById(String id) {
        return this.delegate.findById(id);
    }

    @Override
    public @Nullable OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        return this.delegate.findByToken(token, tokenType);
    }
}
