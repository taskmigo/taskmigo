package io.taskmigo.web.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

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

    private static UserSessionPrincipal principal() {
        return new UserSessionPrincipal(
            UUID.randomUUID(),
            User.withUsername("alice").password("secret").roles("USER").build()
        );
    }
}
