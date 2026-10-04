package io.taskmigo.web.composition.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.taskmigo.web.adapter.in.security.session.UserSessionPrincipal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

class SessionPrincipalSerializationTest {

    /// Verifies that an erased principal keeps stable identity in the JDBC session payload.
    /// Given: a UUID-bearing principal whose password has been erased after login.
    /// Expect: Java serialization preserves UUID, username and authorities without restoring the password.
    @Test
    @DisplayName("preserves the stable principal in Java session serialization")
    void shouldPreservePrincipalWhenSessionPayloadIsSerialized() throws Exception {
        // Arrange
        UserSessionPrincipal principal = principal();
        principal.eraseCredentials();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // Act
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(principal);
        }
        UserSessionPrincipal restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (UserSessionPrincipal) Objects.requireNonNull(input.readObject());
        }
        // Assert
        assertThat(restored.userId()).isEqualTo(principal.userId());
        assertThat(restored.getUsername()).isEqualTo("alice");
        assertThat(restored.getPassword()).isNull();
        assertThat(restored.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

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

    /// Verifies that OAuth persistence does not rely on Jackson's library default for Taskmigo-wide settings.
    /// Given: the dedicated OAuth mapper is constructed by application composition code.
    /// Expect: its construction path explicitly depends on the shared Taskmigo Jackson policy.
    @Test
    @DisplayName("applies the shared Taskmigo Jackson policy to OAuth persistence")
    void shouldApplySharedTaskmigoJacksonPolicyWhenOAuthMapperIsConstructed() {
        // Arrange
        var configuration = new ClassFileImporter()
            .importClasses(OAuthPersistenceConfiguration.class)
            .get(OAuthPersistenceConfiguration.class);

        // Act
        var dependencies = configuration
            .getDirectDependenciesFromSelf()
            .stream()
            .map(dependency -> dependency.getTargetClass().getName())
            .toList();

        // Assert
        assertThat(dependencies).contains("io.taskmigo.foundation.jackson.TaskmigoJackson");
    }

    private static UserSessionPrincipal principal() {
        return new UserSessionPrincipal(
            UUID.randomUUID(),
            User.withUsername("alice").password("secret").roles("USER").build()
        );
    }
}
