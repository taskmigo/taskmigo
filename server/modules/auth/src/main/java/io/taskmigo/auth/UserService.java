package io.taskmigo.auth;

import io.taskmigo.foundation.OffsetPage;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/// Manages global users, credentials, and profile data.
@Service
public class UserService {

    private final UserRepository users;
    private final ObjectAuthorizationService objectAuthorization;

    UserService(UserRepository users, ObjectAuthorizationService objectAuthorization) {
        this.users = users;
        this.objectAuthorization = objectAuthorization;
    }

    /// Creates a user while normalizing zero or more email addresses.
    @Transactional
    public UUID create(
        @Nullable String username,
        @Nullable Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    ) {
        return this.create(username, emails, firstName, lastName, Set.of());
    }

    /// Creates a user with optional direct Role assignments.
    ///
    /// @param username the unique login name for the new User
    /// @param emails the email addresses associated with the new User
    /// @param firstName the User's given name
    /// @param lastName the User's family name
    /// @param roleIds the directly assigned Roles
    /// @return the id of the created User
    @Transactional
    public UUID create(
        @Nullable String username,
        @Nullable Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable Collection<UUID> roleIds
    ) {
        String requiredUsername = required(username, "username");
        if (SystemUser.USERNAME.equals(requiredUsername)) {
            throw new UserException(UserException.Type.BAD_REQUEST, "Username is reserved for the bootstrap user");
        }

        try {
            UUID id = UUID.randomUUID();
            Set<UUID> requestedRoleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
            this.users.saveAndFlush(
                new UserEntity(
                    id,
                    requiredUsername,
                    normalizeEmails(emails),
                    requestedRoleIds,
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

    /// Lists global Users in stable identifier order for offset pagination.
    ///
    /// @param page the one-based page requested by the API client
    /// @param perPage the maximum number of Users to return
    /// @return one offset-paginated page of Users
    @Transactional(readOnly = true)
    public OffsetPage<UserInfo> list(int page, int perPage) {
        return this.list(page, perPage, null);
    }

    /// Lists Users using an optional database-side object authorization predicate.
    @Transactional(readOnly = true)
    public OffsetPage<UserInfo> list(
        int page,
        int perPage,
        ObjectAuthorizationService.@Nullable ObjectAuthorizationPlan authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var result =
            authorization == null
                ? this.users.findAll(pageable)
                : this.users.findAll(this.objectAuthorization.specification(authorization), pageable);
        return new OffsetPage<>(
            result.map(UserService::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    /// Returns the direct Role ids assigned to a User.
    ///
    /// @param userId the User whose effective Roles are resolved
    /// @return the deduplicated direct Role ids
    @Transactional(readOnly = true)
    public Set<UUID> roleIds(UUID userId) {
        UserEntity user = this.users
            .findById(userId)
            .orElseThrow(() -> new UserException(UserException.Type.NOT_FOUND, "User not found"));
        return Set.copyOf(user.roleIds);
    }

    /// Replaces the Statements directly assigned to a User.
    ///
    /// Duplicate ids are normalized. Statement existence is validated by the web orchestration layer before this
    /// owner-module mutation is invoked.
    ///
    /// @param userId the User whose direct Statements are replaced
    /// @param statementIds the complete desired set of directly assigned Statements
    @Transactional
    public void setStatements(UUID userId, Collection<UUID> statementIds) {
        UserEntity user = this.users
            .findById(userId)
            .orElseThrow(() -> new UserException(UserException.Type.NOT_FOUND, "User not found"));
        user.statementIds.clear();
        user.statementIds.addAll(Set.copyOf(statementIds));
        this.users.flush();
    }

    /// Replaces the direct Role assignments for a User.
    @Transactional
    public void setRoles(UUID userId, Collection<UUID> roleIds) {
        UserEntity user = this.users
            .findById(userId)
            .orElseThrow(() -> new UserException(UserException.Type.NOT_FOUND, "User not found"));
        user.roleIds.clear();
        user.roleIds.addAll(Set.copyOf(roleIds));
        this.users.flush();
    }

    /// Reconciles a bootstrap User while preserving credentials and account status.
    @Transactional
    public UUID reconcileBootstrapUser(
        @Nullable String username,
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        Collection<UUID> roleIds,
        Collection<UUID> statementIds
    ) {
        String requiredUsername = required(username, "username");
        Set<String> requestedEmails = normalizeEmails(emails);
        Set<UUID> requestedRoleIds = Set.copyOf(roleIds);
        Set<UUID> requestedStmtIds = Set.copyOf(statementIds);
        UserEntity user = this.users.findByUsername(requiredUsername).orElse(null);
        if (user == null) {
            user = new UserEntity(
                UUID.randomUUID(),
                requiredUsername,
                requestedEmails,
                requestedRoleIds,
                required(firstName, "firstName"),
                required(lastName, "lastName")
            );
            user.statementIds.addAll(requestedStmtIds);
            this.users.saveAndFlush(user);
            return user.id;
        }

        user.emails.clear();
        user.emails.addAll(requestedEmails);
        user.firstName = required(firstName, "firstName");
        user.lastName = required(lastName, "lastName");
        user.roleIds.clear();
        user.roleIds.addAll(requestedRoleIds);
        user.statementIds.clear();
        user.statementIds.addAll(requestedStmtIds);
        this.users.flush();
        return user.id;
    }

    /// Ensures the reserved bootstrap username exists as a regular persisted user.
    ///
    /// Existing profile, status, and credentials are preserved. When an existing bootstrap user has no
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
            SystemUser.USERNAME,
            Set.of(),
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

    private static Set<String> normalizeEmails(@Nullable Collection<String> emails) {
        if (emails == null || emails.isEmpty()) return Set.of();
        return emails
            .stream()
            .map(email -> required(email, "email").toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static UserInfo info(UserEntity user) {
        return new UserInfo(
            user.id,
            user.username,
            user.firstName,
            user.lastName,
            Set.copyOf(user.emails),
            user.displayName()
        );
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) throw new UserException(
            UserException.Type.BAD_REQUEST,
            field + " is required"
        );
        return value.trim();
    }
}
