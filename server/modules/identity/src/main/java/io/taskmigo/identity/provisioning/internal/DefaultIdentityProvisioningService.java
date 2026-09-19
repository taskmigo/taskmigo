package io.taskmigo.identity.provisioning.internal;

import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.GroupService;
import io.taskmigo.identity.provisioning.IdentityProvisioningException;
import io.taskmigo.identity.provisioning.IdentityProvisioningService;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.internal.UserStore;
import io.taskmigo.identity.user.internal.UserStore.UserState;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Reconciles managed Identity state without exposing provisioning semantics through runtime User services.
@Service
class DefaultIdentityProvisioningService implements IdentityProvisioningService {

    private final UserStore users;
    private final SubjectGrantService grants;
    private final GroupService groups;

    DefaultIdentityProvisioningService(UserStore users, SubjectGrantService grants, GroupService groups) {
        this.users = users;
        this.grants = grants;
        this.groups = groups;
    }

    @Override
    @Transactional
    public UUID reconcileUser(
        @Nullable String username,
        @Nullable String passwordHash,
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        Collection<UUID> roleIds,
        Collection<UUID> groupIds
    ) {
        String requiredUsername = required(username, "username");
        Set<String> requestedEmails = normalizeEmails(emails);
        String requiredFirstName = required(firstName, "firstName");
        String requiredLastName = required(lastName, "lastName");

        UserState existing = this.users.findByUsername(requiredUsername).orElse(null);
        UUID id;
        if (existing == null) {
            id = UUID.randomUUID();
            this.users.create(
                new UserState(
                    id,
                    requiredUsername,
                    requestedEmails,
                    requiredFirstName,
                    requiredLastName,
                    true,
                    requiredPassword(requiredUsername, passwordHash)
                )
            );
        } else {
            id = existing.id();
            this.users.updateProfile(id, requestedEmails, requiredFirstName, requiredLastName);
            if (passwordHash != null && !passwordHash.isBlank()) {
                this.users.updatePasswordHash(id, passwordHash);
            }
        }

        this.grants.setRoles(IdentitySubjects.user(id), roleIds);
        this.grants.setStatements(IdentitySubjects.user(id), Set.of());
        this.groups.setGroupsForUser(id, groupIds);
        return id;
    }

    @Override
    @Transactional
    public void deleteUser(String username) {
        UserState existing = this.users.findByUsername(required(username, "username")).orElse(null);
        if (existing == null) {
            return;
        }
        if (SystemUser.USERNAME.equals(existing.username())) {
            throw new IdentityProvisioningException("The system user cannot be deleted");
        }
        this.grants.setRoles(IdentitySubjects.user(existing.id()), Set.of());
        this.grants.setStatements(IdentitySubjects.user(existing.id()), Set.of());
        this.groups.setGroupsForUser(existing.id(), Set.of());
        this.users.delete(existing.id());
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

    private static @Nullable String requiredPassword(String username, @Nullable String passwordHash) {
        if (SystemUser.USERNAME.equals(username) && (passwordHash == null || passwordHash.isBlank())) {
            throw new IdentityProvisioningException("A password hash is required for the system user");
        }
        return passwordHash;
    }
}
