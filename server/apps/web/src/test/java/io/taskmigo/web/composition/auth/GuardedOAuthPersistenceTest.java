package io.taskmigo.web.composition.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.taskmigo.identity.user.application.port.in.api.UserSessionLifecycleService;
import io.taskmigo.web.adapter.in.security.session.UserSessionPrincipal;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class GuardedOAuthPersistenceTest {

    private final UserSessionLifecycleService lifecycle = mock(UserSessionLifecycleService.class);
    private final OAuth2AuthorizationService authorizations = mock(OAuth2AuthorizationService.class);
    private final OAuth2AuthorizationConsentService consents = mock(OAuth2AuthorizationConsentService.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /// Verifies active Users can persist OAuth state under their lifecycle lock.
    /// Given: authorization attributes containing a UUID-bearing authenticated principal.
    /// Expect: the persistence callback runs through the gate.
    @Test
    @DisplayName("saves interactive authorization under the stable User gate")
    void shouldSaveAuthorizationWhenInteractiveUserIsActive() {
        // Arrange
        var principal = principal();
        var authorization = authorization(principal);
        doAnswer(call -> {
            call.<Runnable>getArgument(1).run();
            return true;
        })
            .when(this.lifecycle)
            .runIfActive(eq(principal.userId()), any());
        var service = new GuardedOAuth2AuthorizationService(this.authorizations, this.lifecycle);
        // Act
        service.save(authorization);
        // Assert
        verify(this.authorizations).save(authorization);
    }

    /// Verifies late OAuth writes cannot recreate state after deletion.
    /// Given: authorization belonging to a User rejected by the lifecycle gate.
    /// Expect: an OAuth error and no persistence call.
    @Test
    @DisplayName("rejects interactive OAuth writes after deletion")
    void shouldRejectAuthorizationWhenUserIsInactive() {
        // Arrange
        var authorization = authorization(principal());
        var service = new GuardedOAuth2AuthorizationService(this.authorizations, this.lifecycle);
        // Act + Assert
        assertThatThrownBy(() -> service.save(authorization)).isInstanceOf(OAuth2AuthenticationException.class);
        verify(this.authorizations, never()).save(any());
    }

    /// Verifies machine authentication does not acquire interactive User requirements.
    /// Given: a client-credentials authorization with no interactive principal.
    /// Expect: the existing library service stores it normally.
    @Test
    @DisplayName("preserves client-credentials authorization persistence")
    void shouldSaveNormallyWhenGrantUsesClientCredentials() {
        // Arrange
        var authorization = OAuth2Authorization.withRegisteredClient(client())
            .principalName("machine")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .build();
        var service = new GuardedOAuth2AuthorizationService(this.authorizations, this.lifecycle);
        // Act
        service.save(authorization);
        // Assert
        verify(this.authorizations).save(authorization);
    }

    /// Verifies consent writes carry stable request identity instead of resolving a reused username.
    /// Given: a consent request whose authenticated User has already been deleted.
    /// Expect: the consent save is rejected and no row is recreated.
    @Test
    @DisplayName("rejects consent saves after the current User is deleted")
    void shouldRejectConsentWhenUserIsInactive() {
        // Arrange
        var principal = principal();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())
        );
        var consent = OAuth2AuthorizationConsent.withId("client", "alice").scope("openid").build();
        var service = new GuardedOAuth2AuthorizationConsentService(this.consents, this.lifecycle);
        // Act + Assert
        assertThatThrownBy(() -> service.save(consent)).isInstanceOf(OAuth2AuthenticationException.class);
        verify(this.consents, never()).save(any());
    }

    /// Verifies active consent persistence uses the UUID from the current authenticated request.
    /// Given: a matching consent and an ACTIVE principal accepted by the lifecycle gate.
    /// Expect: the consent is saved inside the gate callback.
    @Test
    @DisplayName("saves consent under the current stable User gate")
    void shouldSaveConsentWhenUserIsActive() {
        // Arrange
        var principal = principal();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())
        );
        var consent = OAuth2AuthorizationConsent.withId("client", "alice").scope("profile").build();
        doAnswer(call -> {
            call.<Runnable>getArgument(1).run();
            return true;
        })
            .when(this.lifecycle)
            .runIfActive(eq(principal.userId()), any());
        var service = new GuardedOAuth2AuthorizationConsentService(this.consents, this.lifecycle);
        // Act
        service.save(consent);
        // Assert
        verify(this.consents).save(consent);
        verify(this.lifecycle).runIfActive(eq(principal.userId()), any());
    }

    private static UserSessionPrincipal principal() {
        return new UserSessionPrincipal(
            UUID.randomUUID(),
            User.withUsername("alice").password("secret").roles("USER").build()
        );
    }

    private static RegisteredClient client() {
        return RegisteredClient.withId("client")
            .clientId("browser")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/callback")
            .build();
    }

    private static OAuth2Authorization authorization(UserSessionPrincipal principal) {
        return OAuth2Authorization.withRegisteredClient(client())
            .principalName("alice")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .attribute(
                Principal.class.getName(),
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())
            )
            .build();
    }
}
