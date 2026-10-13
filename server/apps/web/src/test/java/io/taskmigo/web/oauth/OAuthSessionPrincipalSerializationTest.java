package io.taskmigo.web.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.web.security.session.UserSessionPrincipal;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import tools.jackson.core.type.TypeReference;

class OAuthSessionPrincipalSerializationTest {

    /// Verifies OAuth authorization persistence does not downgrade the custom principal to username-only UserDetails.
    /// Given: OAuth attributes containing an authenticated UUID-bearing principal with erased credentials.
    /// Expect: the configured Jackson mapper restores the exact principal type and stable identity.
    @Test
    @DisplayName("preserves stable principal identity in OAuth JSON attributes")
    void shouldPreservePrincipalWhenOAuthAttributesRoundTrip() {
        // Arrange
        UserSessionPrincipal principal = principal();
        principal.eraseCredentials();
        var token = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        var mapper = OAuthPersistenceConfiguration.authorizationMapper();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(Principal.class.getName(), token);
        // Act
        Map<String, Object> restored = mapper.readValue(
            mapper.writeValueAsString(attributes),
            new TypeReference<Map<String, Object>>() {}
        );
        var authentication = (UsernamePasswordAuthenticationToken) Objects.requireNonNull(
            restored.get(Principal.class.getName())
        );
        // Assert
        assertThat(authentication.getPrincipal()).isInstanceOf(UserSessionPrincipal.class);
        var restoredPrincipal = (UserSessionPrincipal) authentication.getPrincipal();
        assertThat(restoredPrincipal.userId()).isEqualTo(principal.userId());
        assertThat(restoredPrincipal.getUsername()).isEqualTo("alice");
        assertThat(restoredPrincipal.getPassword()).isNull();
    }

    private static UserSessionPrincipal principal() {
        return new UserSessionPrincipal(
            UUID.randomUUID(),
            User.withUsername("alice").password("secret").roles("USER").build()
        );
    }
}
