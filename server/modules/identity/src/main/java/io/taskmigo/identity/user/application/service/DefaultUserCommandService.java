package io.taskmigo.identity.user.application.service;

import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.in.internal.UserMutationResult;
import io.taskmigo.identity.user.application.port.out.UserCommandRepository;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.Username;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Applies canonical User mutations through a domain-shaped outbound repository port.
///
/// Transaction scope is owned by the calling use case: runtime registration and managed provisioning both invoke this
/// service from their enclosing application transaction.
public final class DefaultUserCommandService implements UserCommandService {

    private final UserCommandRepository users;

    public DefaultUserCommandService(UserCommandRepository users) {
        this.users = users;
    }

    @Override
    public UUID createRuntime(
        @Nullable String username,
        @Nullable Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    ) {
        User user = User.register(UUID.randomUUID(), username, emails, firstName, lastName);
        this.users.save(user);
        return user.id();
    }

    @Override
    public UserMutationResult reconcileManaged(
        @Nullable String username,
        @Nullable String initialPasswordHash,
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    ) {
        Username normalizedUsername = Username.of(username);
        User existing = this.users.findByUsername(normalizedUsername).orElse(null);
        if (existing == null) {
            User created = User.managed(
                UUID.randomUUID(),
                normalizedUsername.value(),
                initialPasswordHash,
                emails,
                firstName,
                lastName
            );
            this.users.save(created);
            return new UserMutationResult(created.id(), true, true);
        }

        var beforeProfile = existing.profile();
        String beforePasswordHash = existing.credential().passwordHash();

        boolean profileChanged = existing.reconcileProfile(emails, firstName, lastName);
        boolean credentialChanged = existing.initializeCredential(initialPasswordHash);
        boolean changed = profileChanged || credentialChanged;
        if (changed) {
            this.users.save(existing);
        }

        List<AuditChange> changes = new ArrayList<>();
        var afterProfile = existing.profile();
        if (!beforeProfile.emails().equals(afterProfile.emails())) {
            changes.add(
                UserAuditChanges.visible("emails", ordered(beforeProfile.emails()), ordered(afterProfile.emails()))
            );
        }
        if (!beforeProfile.firstName().equals(afterProfile.firstName())) {
            changes.add(UserAuditChanges.visible("firstName", beforeProfile.firstName(), afterProfile.firstName()));
        }
        if (!beforeProfile.lastName().equals(afterProfile.lastName())) {
            changes.add(UserAuditChanges.visible("lastName", beforeProfile.lastName(), afterProfile.lastName()));
        }

        String afterPasswordHash = existing.credential().passwordHash();
        if (!Objects.equals(beforePasswordHash, afterPasswordHash)) {
            changes.add(UserAuditChanges.visible("passwordHash", beforePasswordHash, afterPasswordHash));
        }

        return new UserMutationResult(existing.id(), false, changed, changes);
    }

    @Override
    public Optional<User> findByUsername(@Nullable String username) {
        return this.users.findByUsername(Username.of(username));
    }

    @Override
    public void delete(User user) {
        this.users.delete(user);
    }

    private static List<String> ordered(Collection<String> values) {
        return values.stream().sorted().toList();
    }
}
