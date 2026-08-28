package io.taskmigo.user;

import io.taskmigo.organization.OrganizationService;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/// Manages organization-owned users plus the reserved global bootstrap identity used for platform administration.
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
        String requiredUsername = required(username, "username");
        if (SystemUser.USERNAME.equals(requiredUsername)) {
            throw new UserException(UserException.Type.BAD_REQUEST, "Username is reserved for the bootstrap user");
        }
        String normalizedEmail = required(email, "email").toLowerCase(Locale.ROOT);
        try {
            UUID id = UUID.randomUUID();
            this.users.saveAndFlush(
                new UserEntity(
                    id,
                    organizationId,
                    requiredUsername,
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
        return new UserInfo(user.id, user.organizationId, user.displayName, user.system);
    }

    /// Finds persisted identity, credentials, and account state for an authentication adapter without exposing the entity.
    @Transactional(readOnly = true)
    public Optional<AuthenticationInfo> findForAuthentication(String username) {
        return this.users
            .findByUsername(username)
            .map(user ->
                new AuthenticationInfo(
                    user.id,
                    user.username,
                    user.displayName,
                    UserStatus.ACTIVE.equals(user.status),
                    user.system,
                    user.passwordHash
                )
            );
    }

    /// Reconciles the reserved platform bootstrap identity in a serializable transaction.
    ///
    /// An existing bootstrap user keeps its persisted password hash; the supplied hash is used only for first creation.
    /// The method returns `false` when the user does not exist and no initialization password hash was supplied.
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean reconcileSystemUser(@Nullable String initialPasswordHash) {
        Optional<UserEntity> byUsername = this.users.findByUsername(SystemUser.USERNAME);
        if (byUsername.isPresent()) {
            UserEntity user = byUsername.orElseThrow();
            this.validateSystemUser(user);
            user.status = UserStatus.ACTIVE;
            user.displayName = SystemUser.DISPLAY_NAME;
            return true;
        }

        if (this.users.existsById(SystemUser.ID)) {
            throw new IllegalStateException("Bootstrap user id is occupied by a non-system user");
        }
        if (initialPasswordHash == null || initialPasswordHash.isBlank()) return false;

        this.users.saveAndFlush(UserEntity.system(initialPasswordHash));
        return true;
    }

    public record UserInfo(UUID id, @Nullable UUID organizationId, String displayName, boolean system) {}

    /// Stable persisted user identity and credential state required by authentication adapters.
    public record AuthenticationInfo(
        UUID id,
        String username,
        String displayName,
        boolean active,
        boolean system,
        @Nullable String passwordHash
    ) {}

    private void validateSystemUser(UserEntity user) {
        if (!user.system || !SystemUser.ID.equals(user.id)) {
            throw new IllegalStateException("Reserved bootstrap username is occupied by a non-system user");
        }
        if (user.passwordHash == null || user.passwordHash.isBlank()) {
            throw new IllegalStateException("Bootstrap user has no persisted password hash");
        }
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) throw new UserException(
            UserException.Type.BAD_REQUEST,
            field + " is required"
        );
        return value.trim();
    }
}
