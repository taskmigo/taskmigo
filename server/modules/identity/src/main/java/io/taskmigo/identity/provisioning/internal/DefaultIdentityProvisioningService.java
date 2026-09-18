package io.taskmigo.identity.provisioning.internal;

import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.identity.authorization.IdentitySubjects;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/// Reconciles managed Identity state without exposing provisioning semantics through runtime User services.
@Service
final class DefaultIdentityProvisioningService implements IdentityProvisioningService {

    private final UserStore users;
    private final SubjectGrantService grants;

    DefaultIdentityProvisioningService(UserStore users, SubjectGrantService grants) {
        this.users = users;
        this.grants = grants;
    }

    @Override
    @Transactional
    public UUID reconcileUser(
        @Nullable String username,
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        Collection<UUID> roleIds,
        Collection<UUID> statementIds
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
                new UserState(id, requiredUsername, requestedEmails, requiredFirstName, requiredLastName, true, null)
            );
        } else {
            id = existing.id();
            this.users.updateProfile(id, requestedEmails, requiredFirstName, requiredLastName);
        }

        this.grants.setRoles(IdentitySubjects.user(id), roleIds);
        this.grants.setStatements(IdentitySubjects.user(id), statementIds);
        return id;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void reconcileSystemUser(@Nullable String initialPasswordHash) {
        UserState existing = this.users.findByUsername(SystemUser.USERNAME).orElse(null);
        if (existing != null) {
            if (existing.passwordHash() == null && initialPasswordHash != null && !initialPasswordHash.isBlank()) {
                this.users.updatePasswordHash(existing.id(), initialPasswordHash);
            }
            return;
        }

        if (initialPasswordHash == null || initialPasswordHash.isBlank()) {
            throw new IdentityProvisioningException(
                "Initial password hash is required when the system user has not been initialized"
            );
        }

        this.users.create(
            new UserState(
                UUID.randomUUID(),
                SystemUser.USERNAME,
                Set.of(),
                SystemUser.FIRST_NAME,
                SystemUser.LAST_NAME,
                true,
                initialPasswordHash
            )
        );
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
