package io.taskmigo.web.adapter.in.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import io.taskmigo.identity.user.application.port.in.api.UserSessionLifecycleService;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;

class GuardedSessionRepositoryTest {

    private final UserService users = mock(UserService.class);
    private final UserSessionLifecycleService lifecycle = mock(UserSessionLifecycleService.class);
    private final MapSessionRepository delegate = new MapSessionRepository(new ConcurrentHashMap<>());
    private final GuardedSessionRepository<MapSession> sessions = new GuardedSessionRepository<>(
        this.delegate,
        this.users,
        this.lifecycle
    );

    /// Verifies unauthenticated sessions remain usable for saved requests and CSRF.
    /// Given: a session with a saved-request attribute and no authenticated principal.
    /// Expect: saving and reading preserve the attribute without a User lookup.
    @Test
    @DisplayName("preserves anonymous session state")
    void shouldPreserveStateWhenSessionIsAnonymous() {
        // Arrange
        MapSession session = this.sessions.createSession();
        session.setAttribute("saved-request", "/oauth2/authorize");
        // Act
        this.sessions.save(session);
        MapSession loaded = this.sessions.findById(session.getId());
        // Assert
        assertThat(loaded).isNotNull();
        assertThat(loaded.<String>getAttribute("saved-request")).isEqualTo("/oauth2/authorize");
    }

    /// Verifies a valid authenticated session retains its stable ownership.
    /// Given: an ACTIVE User and its UUID-bearing principal.
    /// Expect: the guarded save executes and the session is readable by that UUID.
    @Test
    @DisplayName("persists authenticated sessions for an active stable User id")
    void shouldPersistSessionWhenStableUserIsActive() {
        // Arrange
        UUID id = UUID.randomUUID();
        MapSession session = authenticated(id);
        doAnswer(call -> {
            call.<Runnable>getArgument(1).run();
            return true;
        })
            .when(this.lifecycle)
            .runIfActive(eq(id), any());
        when(this.users.find(id)).thenReturn(
            Optional.of(
                new UserInfo(id, "alice", "Alice", "Example", Set.of(), "Alice Example", UserStatus.ACTIVE, null)
            )
        );
        // Act
        this.sessions.save(session);
        MapSession loaded = this.sessions.findById(session.getId());
        // Assert
        assertThat(loaded).isNotNull();
        assertThat(Objects.requireNonNull(UserSessionPrincipal.fromSession(loaded)).userId()).isEqualTo(id);
    }

    /// Verifies a response completing after deletion cannot recreate session state.
    /// Given: a persisted session and a lifecycle gate rejecting its User.
    /// Expect: a subsequent save removes the session instead of persisting it.
    @Test
    @DisplayName("removes stale session state when a save loses to deletion")
    void shouldRemoveSessionWhenSaveIsRejected() {
        // Arrange
        MapSession session = authenticated(UUID.randomUUID());
        this.delegate.save(session);
        // Act
        this.sessions.save(session);
        // Assert
        assertThat(this.delegate.findById(session.getId())).isNull();
    }

    /// Verifies stored authentication is revalidated on read.
    /// Given: an old authenticated session whose User is no longer available through Identity.
    /// Expect: reading it returns no session and removes the stored state.
    @Test
    @DisplayName("rejects stale authentication on session reads")
    void shouldRemoveSessionWhenOwnerIsUnavailable() {
        // Arrange
        MapSession session = authenticated(UUID.randomUUID());
        this.delegate.save(session);
        // Act
        MapSession loaded = this.sessions.findById(session.getId());
        // Assert
        assertThat(loaded).isNull();
        assertThat(this.delegate.findById(session.getId())).isNull();
    }

    /// Verifies username-only authentication cannot acquire the identity of a replacement User.
    /// Given: an authenticated session containing the previous principal representation without UUID.
    /// Expect: its state is removed and authentication is not returned.
    @Test
    @DisplayName("rejects authenticated sessions without stable User identity")
    void shouldRejectSessionWhenPrincipalHasNoUserId() {
        // Arrange
        MapSession session = this.sessions.createSession();
        var principal = User.withUsername("alice").password("secret").roles("USER").build();
        session.setAttribute(
            "SPRING_SECURITY_CONTEXT",
            new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())
            )
        );
        this.delegate.save(session);
        // Act
        this.sessions.save(session);
        // Assert
        assertThat(this.delegate.findById(session.getId())).isNull();
    }

    private MapSession authenticated(UUID id) {
        MapSession session = this.sessions.createSession();
        UserSessionPrincipal principal = new UserSessionPrincipal(
            id,
            User.withUsername("alice").password("secret").roles("USER").build()
        );
        session.setAttribute(
            "SPRING_SECURITY_CONTEXT",
            new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())
            )
        );
        return session;
    }
}
