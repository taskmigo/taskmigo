package io.taskmigo.identity.user.internal;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.user.AuthenticationInfo;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserService;
import io.taskmigo.identity.user.internal.UserStore.UserState;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Implements User use cases independently from the JPA adapter.
@Service
public class DefaultUserService implements UserService {

    private final UserStore users;
    private final SubjectGrantService grants;

    public DefaultUserService(UserStore users, SubjectGrantService grants) {
        this.users = users;
        this.grants = grants;
    }

    @Override
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
            throw new UserException(UserException.Type.BAD_REQUEST, "Username is reserved for the system user");
        }

        UUID id = UUID.randomUUID();
        this.users.create(
            new UserState(
                id,
                requiredUsername,
                normalizeEmails(emails),
                required(firstName, "firstName"),
                required(lastName, "lastName"),
                true,
                null
            )
        );
        this.grants.setRoles(IdentitySubjects.user(id), roleIds == null ? Set.of() : roleIds);
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfo require(UUID id) {
        return this.users
            .find(id)
            .map(DefaultUserService::info)
            .orElseThrow(() -> new UserException(UserException.Type.NOT_FOUND, "User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticationInfo> findForAuthentication(String username) {
        return this.users.findByUsername(username).map(DefaultUserService::authentication);
    }

    @Override
    @Transactional(readOnly = true)
    public OffsetPage<UserInfo> list(
        int page,
        int perPage,
        QueryPredicate<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    ) {
        return this.users.list(page, perPage, filter, authorization);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> roleIds(UUID userId) {
        this.require(userId);
        return this.grants.roleIds(IdentitySubjects.user(userId));
    }

    @Override
    @Transactional
    public void setStatements(UUID userId, Collection<UUID> statementIds) {
        this.require(userId);
        this.grants.setStatements(IdentitySubjects.user(userId), statementIds);
    }

    @Override
    @Transactional
    public void setRoles(UUID userId, Collection<UUID> roleIds) {
        this.require(userId);
        this.grants.setRoles(IdentitySubjects.user(userId), roleIds);
    }

    private static UserInfo info(UserState user) {
        return new UserInfo(
            user.id(),
            user.username(),
            user.firstName(),
            user.lastName(),
            user.emails(),
            displayName(user.firstName(), user.lastName())
        );
    }

    private static AuthenticationInfo authentication(UserState user) {
        return new AuthenticationInfo(
            user.id(),
            user.username(),
            displayName(user.firstName(), user.lastName()),
            user.active(),
            user.passwordHash()
        );
    }

    private static String displayName(String firstName, String lastName) {
        return (firstName + " " + lastName).trim();
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

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new UserException(UserException.Type.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }
}
