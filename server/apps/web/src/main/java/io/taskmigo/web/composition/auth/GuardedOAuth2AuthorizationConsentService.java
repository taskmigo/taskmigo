package io.taskmigo.web.composition.auth;

import io.taskmigo.identity.user.application.port.in.api.UserSessionLifecycleService;
import io.taskmigo.web.adapter.in.security.session.UserSessionPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;

/// Keeps interactive consent writes ordered with deletion of the same stable User identity.
final class GuardedOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private final OAuth2AuthorizationConsentService delegate;
    private final UserSessionLifecycleService lifecycle;

    GuardedOAuth2AuthorizationConsentService(
        OAuth2AuthorizationConsentService delegate,
        UserSessionLifecycleService lifecycle
    ) {
        this.delegate = delegate;
        this.lifecycle = lifecycle;
    }

    @Override
    public void save(OAuth2AuthorizationConsent consent) {
        UserSessionPrincipal principal = UserSessionPrincipal.fromAuthentication(
            SecurityContextHolder.getContext().getAuthentication()
        );
        if (
            principal == null ||
            !consent.getPrincipalName().equals(principal.getUsername()) ||
            !this.lifecycle.runIfActive(principal.userId(), () -> this.delegate.save(consent))
        ) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.ACCESS_DENIED);
        }
    }

    @Override
    public void remove(OAuth2AuthorizationConsent consent) {
        this.delegate.remove(consent);
    }

    @Override
    public @Nullable OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        return this.delegate.findById(registeredClientId, principalName);
    }
}
