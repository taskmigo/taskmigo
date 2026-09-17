package io.taskmigo.identity.persistence.user;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.user.AuthenticationInfo;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserService;
import io.taskmigo.query.QueryPredicate;
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

/// Manages global users, credentials, and profile data while delegating authorization grants to Access Control.
@Service
public class JpaUserOperations implements UserService {

    private final UserRepository users;
    private final SubjectGrantService grants;
    private final QueryPredicateBinder<UserInfo, UserEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> objectBinder;

    public JpaUserOperations(
        UserRepository users,
        SubjectGrantService grants,
        QueryPredicateBinder<UserInfo, UserEntity> queryBinder,
        ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> objectBinder
    ) {
        this.users = users;
        this.grants = grants;
        this.queryBinder = queryBinder;
        this.objectBinder = objectBinder;
    }

    /// Creates a User and delegates optional direct Role assignments to Access Control.
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
            this.users.saveAndFlush(
                new UserEntity(
                    id,
                    requiredUsername,
                    normalizeEmails(emails),
                    required(firstName, "firstName"),
                    required(lastName, "lastName")
                )
            );
            this.grants.setRoles(IdentitySubjects.user(id), roleIds == null ? Set.of() : roleIds);
            return id;
        } catch (DataIntegrityViolationException exception) {
            throw new UserException(UserException.Type.CONFLICT, "Username or email already exists", exception);
        }
    }

    /// Returns a stable User snapshot for cross-module validation and presentation.
    @Transactional(readOnly = true)
    public UserInfo require(UUID id) {
        return info(this.requireEntity(id));
    }

    /// Finds persisted identity, credentials, and account state for an authentication adapter without exposing the entity.
    @Transactional(readOnly = true)
    public Optional<AuthenticationInfo> findForAuthentication(String username) {
        return this.users
            .findByUsername(username)
            .map(user ->
                new AuthenticationInfo(
                    user.id(),
                    user.username(),
                    user.displayName(),
                    UserStatus.ACTIVE.equals(user.status()),
                    user.passwordHash()
                )
            );
    }

    /// Lists Users by binding both opaque predicates before pagination.
    @Transactional(readOnly = true)
    public OffsetPage<UserInfo> list(
        int page,
        int perPage,
        QueryPredicate<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var result = this.users.findAll(
            this.queryBinder.bind(filter).and(this.objectBinder.bind(authorization)),
            pageable
        );
        return new OffsetPage<>(
            result.map(JpaUserOperations::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    /// Returns the direct Role ids bound to a User by Access Control.
    @Transactional(readOnly = true)
    public Set<UUID> roleIds(UUID userId) {
        this.requireEntity(userId);
        return this.grants.roleIds(IdentitySubjects.user(userId));
    }

    /// Replaces the Statements directly bound to a User through Access Control.
    @Transactional
    public void setStatements(UUID userId, Collection<UUID> statementIds) {
        this.requireEntity(userId);
        this.grants.setStatements(IdentitySubjects.user(userId), statementIds);
    }

    /// Replaces the Roles directly bound to a User through Access Control.
    @Transactional
    public void setRoles(UUID userId, Collection<UUID> roleIds) {
        this.requireEntity(userId);
        this.grants.setRoles(IdentitySubjects.user(userId), roleIds);
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
        UserEntity user = this.users.findByUsername(requiredUsername).orElse(null);
        if (user == null) {
            user = new UserEntity(
                UUID.randomUUID(),
                requiredUsername,
                requestedEmails,
                required(firstName, "firstName"),
                required(lastName, "lastName")
            );
            this.users.saveAndFlush(user);
        } else {
            user.replaceEmails(requestedEmails);
            user.updateProfile(required(firstName, "firstName"), required(lastName, "lastName"));
            this.users.flush();
        }
        this.grants.setRoles(IdentitySubjects.user(user.id()), roleIds);
        this.grants.setStatements(IdentitySubjects.user(user.id()), statementIds);
        return user.id();
    }

    /// Ensures the reserved bootstrap username exists as a regular persisted User.
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean reconcileSystemUser(@Nullable String initialPasswordHash) {
        Optional<UserEntity> existing = this.users.findByUsername(SystemUser.USERNAME);
        if (existing.isPresent()) {
            UserEntity user = existing.orElseThrow();
            if (user.passwordHash() == null && initialPasswordHash != null && !initialPasswordHash.isBlank()) {
                user.setPasswordHash(initialPasswordHash);
            }
            return true;
        }

        if (initialPasswordHash == null || initialPasswordHash.isBlank()) {
            return false;
        }

        UserEntity user = new UserEntity(
            UUID.randomUUID(),
            SystemUser.USERNAME,
            Set.of(),
            SystemUser.FIRST_NAME,
            SystemUser.LAST_NAME
        );
        user.setPasswordHash(initialPasswordHash);
        this.users.saveAndFlush(user);
        return true;
    }

    private UserEntity requireEntity(UUID userId) {
        return this.users
            .findById(userId)
            .orElseThrow(() -> new UserException(UserException.Type.NOT_FOUND, "User not found"));
    }

    private static Set<String> normalizeEmails(@Nullable Collection<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return Set.of();
        }
        return emails
            .stream()
            .map(email -> required(email, "email").toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static UserInfo info(UserEntity user) {
        return new UserInfo(
            user.id(),
            user.username(),
            user.firstName(),
            user.lastName(),
            user.emails(),
            user.displayName()
        );
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new UserException(UserException.Type.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }
}
