package io.taskmigo.web.adapter.in.security.session;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serial;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.session.Session;

/// Preserves immutable User identity across servlet sessions and OAuth authorization attributes.
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public final class UserSessionPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final UserDetails user;

    @JsonCreator
    public UserSessionPrincipal(@JsonProperty("userId") UUID userId, @JsonProperty("user") UserDetails user) {
        this.userId = Objects.requireNonNull(userId);
        this.user = Objects.requireNonNull(user);
    }

    public UUID userId() {
        return this.userId;
    }

    /// Reads the authenticated principal without treating anonymous state as User-owned authentication.
    public static @Nullable Authentication authentication(Session session) {
        Object context = session.getAttribute("SPRING_SECURITY_CONTEXT");
        if (!(context instanceof SecurityContext security)) {
            return null;
        }
        Authentication authentication = security.getAuthentication();
        return authentication instanceof AnonymousAuthenticationToken ? null : authentication;
    }

    /// Extracts stable identity only from an authenticated Taskmigo principal.
    public static @Nullable UserSessionPrincipal fromSession(Session session) {
        return fromAuthentication(authentication(session));
    }

    public static @Nullable UserSessionPrincipal fromAuthentication(@Nullable Authentication authentication) {
        return authentication != null &&
            authentication.isAuthenticated() &&
            authentication.getPrincipal() instanceof UserSessionPrincipal principal
            ? principal
            : null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.user.getAuthorities();
    }

    @Override
    public @Nullable String getPassword() {
        return this.user.getPassword();
    }

    @Override
    public String getUsername() {
        return this.user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return this.user.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return this.user.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return this.user.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return this.user.isEnabled();
    }

    @Override
    public void eraseCredentials() {
        if (this.user instanceof CredentialsContainer credentials) {
            credentials.eraseCredentials();
        }
    }
}
