package io.taskmigo.identity.user.application.service;

import io.taskmigo.audit.AuditActor;
import io.taskmigo.audit.AuditChange;
import io.taskmigo.audit.AuditEvent;
import io.taskmigo.audit.AuditPublisher;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.in.internal.UserMutationResult;
import io.taskmigo.identity.user.application.port.out.UserCommandRepository;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.UserProfile;
import io.taskmigo.identity.user.domain.Username;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
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
    private final AuditPublisher audit;

    public DefaultUserCommandService(UserCommandRepository users, AuditPublisher audit) {
        this.users = users;
        this.audit = audit;
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

        UserProfile beforeProfile = existing.profile();
        boolean profileChanged = existing.reconcileProfile(emails, firstName, lastName);
        boolean credentialChanged = existing.initializeCredential(initialPasswordHash);
        boolean changed = profileChanged || credentialChanged;
        if (changed) {
            this.users.save(existing);
            this.publishManagedUpdate(existing, beforeProfile, credentialChanged);
        }
        return new UserMutationResult(existing.id(), false, changed);
    }

    @Override
    public Optional<User> findByUsername(@Nullable String username) {
        return this.users.findByUsername(Username.of(username));
    }

    @Override
    public void delete(User user) {
        this.users.delete(user);
    }

    private void publishManagedUpdate(User user, UserProfile beforeProfile, boolean credentialChanged) {
        UserProfile afterProfile = user.profile();
        var changes = new ArrayList<AuditChange>();
        if (!beforeProfile.emails().equals(afterProfile.emails())) {
            changes.add(AuditChange.visible("emails", beforeProfile.emails(), afterProfile.emails()));
        }
        if (!beforeProfile.firstName().equals(afterProfile.firstName())) {
            changes.add(AuditChange.visible("firstName", beforeProfile.firstName(), afterProfile.firstName()));
        }
        if (!beforeProfile.lastName().equals(afterProfile.lastName())) {
            changes.add(AuditChange.visible("lastName", beforeProfile.lastName(), afterProfile.lastName()));
        }
        if (credentialChanged) {
            changes.add(AuditChange.sensitive("password"));
        }
        if (changes.isEmpty()) {
            return;
        }

        this.audit.publish(
            new AuditEvent(
                UUID.randomUUID(),
                "user",
                user.id(),
                new AuditActor(SystemUser.USERNAME, SystemUser.FIRST_NAME + " " + SystemUser.LAST_NAME),
                Instant.now(),
                changes
            )
        );
    }
}
