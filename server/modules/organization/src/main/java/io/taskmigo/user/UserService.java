package io.taskmigo.user;

import io.taskmigo.organization.OrganizationService;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages users owned by organizations and exposes stable user identity snapshots to collaborating modules.
@Service
public class UserService {

    private final UserRepository users;
    private final OrganizationService organizations;

    UserService(UserRepository users, OrganizationService organizations) {
        this.users = users;
        this.organizations = organizations;
    }

    /// Creates a user after validating its owning organization and normalizing the email address.
    @Transactional
    public UUID create(
        UUID organizationId,
        @Nullable String username,
        @Nullable String email,
        @Nullable String displayName
    ) {
        this.organizations.require(organizationId);
        String normalizedEmail = required(email, "email").toLowerCase(Locale.ROOT);
        try {
            UUID id = UUID.randomUUID();
            this.users.saveAndFlush(
                new UserEntity(
                    id,
                    organizationId,
                    required(username, "username"),
                    normalizedEmail,
                    required(displayName, "displayName")
                )
            );
            return id;
        } catch (DataIntegrityViolationException exception) {
            throw new UserException(
                UserException.Type.CONFLICT,
                "Username or normalized email already exists",
                exception
            );
        }
    }

    /// Returns a stable user snapshot for cross-module validation and presentation.
    @Transactional(readOnly = true)
    public UserInfo require(UUID id) {
        UserEntity user = this.users
            .findById(id)
            .orElseThrow(() -> new UserException(UserException.Type.NOT_FOUND, "User not found"));
        return new UserInfo(user.id, user.organizationId, user.displayName);
    }

    /// Finds persisted identity and account state for an authentication adapter without exposing the user entity.
    @Transactional(readOnly = true)
    public Optional<AuthenticationInfo> findForAuthentication(String username) {
        return this.users
            .findByUsername(username)
            .map(user ->
                new AuthenticationInfo(user.id, user.username, user.displayName, UserStatus.ACTIVE.equals(user.status))
            );
    }

    public record UserInfo(UUID id, UUID organizationId, String displayName) {}

    /// Stable persisted user identity required by authentication adapters.
    public record AuthenticationInfo(UUID id, String username, String displayName, boolean active) {}

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) throw new UserException(
            UserException.Type.BAD_REQUEST,
            field + " is required"
        );
        return value.trim();
    }
}
