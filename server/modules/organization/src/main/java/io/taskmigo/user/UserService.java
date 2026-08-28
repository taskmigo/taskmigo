package io.taskmigo.user;

import io.taskmigo.organization.OrganizationService;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/// Manages users with optional organization membership, credentials, and profile data.
@Service
public class UserService {

    private final UserRepository users;
    private final OrganizationService organizations;

    UserService(UserRepository users, OrganizationService organizations) {
        this.users = users;
        this.organizations = organizations;
    }

    /// Creates a user, normalizing zero or more email addresses and validating an optional owning organization.
    @Transactional
    public UUID create(
        @Nullable UUID organizationId,
        @Nullable String username,
        @Nullable Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    ) {
        if (organizationId != null) this.organizations.require(organizationId);
        String requiredUsername = required(username, "username");
        if (SystemUser.USERNAME.equals(requiredUsername)) {
            throw new UserException(UserException.Type.BAD_REQUEST, "Username is reserved for the bootstrap user");
        }

        try {
            UUID id = UUID.randomUUID();
            this.users.saveAndFlush(
                new UserEntity(
                    id,
                    organizationId,
                    requiredUsername,
                    normalizeEmails(emails),
                    required(firstName, "firstName"),
                    required(lastName, "lastName")
                )
            );
            return id;
        } catch (DataIntegrityViolationException exception) {
            throw new UserException(UserException.Type.CONFLICT, "Username or email already exists", exception);
        }
    }

    /// Returns a stable user snapshot for cross-module validation and presentation.
    @Transactional(readOnly = true)
    public UserInfo require(UUID id) {
        UserEntity user = this.users
            .findById(id)
            .orElseThrow(() -> new UserException(UserException.Type.NOT_FOUND, "User not found"));
        return new UserInfo(
            user.id,
            user.organizationId,
            user.username,
            user.firstName,
            user.lastName,
            Set.copyOf(user.emails),
            user.displayName()
        );
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
                    user.displayName(),
                    UserStatus.ACTIVE.equals(user.status),
                    user.passwordHash
                )
            );
    }

    /// Ensures the reserved bootstrap username exists as a regular persisted user.
    ///
    /// Existing profile, organization, status, and credentials are preserved. When an existing bootstrap user has no
    /// password credential yet, a supplied initialization hash is persisted once. A new bootstrap user requires an
    /// initialization password hash.
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean reconcileSystemUser(@Nullable String initialPasswordHash) {
        Optional<UserEntity> existing = this.users.findByUsername(SystemUser.USERNAME);
        if (existing.isPresent()) {
            UserEntity user = existing.orElseThrow();
            if (user.passwordHash == null && initialPasswordHash != null && !initialPasswordHash.isBlank()) {
                user.passwordHash = initialPasswordHash;
            }
            return true;
        }

        if (initialPasswordHash == null || initialPasswordHash.isBlank()) return false;

        UserEntity user = new UserEntity(
            UUID.randomUUID(),
            null,
            SystemUser.USERNAME,
            Set.of(),
            SystemUser.FIRST_NAME,
            SystemUser.LAST_NAME
        );
        user.passwordHash = initialPasswordHash;
        this.users.saveAndFlush(user);
        return true;
    }

    /// Stable user identity and profile data required by other modules.
    public record UserInfo(
        UUID id,
        @Nullable UUID organizationId,
        String username,
        String firstName,
        String lastName,
        Set<String> emails,
        String displayName
    ) {}

    /// Stable persisted user identity and credential state required by authentication adapters.
    public record AuthenticationInfo(
        UUID id,
        String username,
        String displayName,
        boolean active,
        @Nullable String passwordHash
    ) {}

    private static Set<String> normalizeEmails(@Nullable Set<String> emails) {
        if (emails == null || emails.isEmpty()) return Set.of();
        return emails
            .stream()
            .map(email -> required(email, "email").toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) throw new UserException(
            UserException.Type.BAD_REQUEST,
            field + " is required"
        );
        return value.trim();
    }
}
