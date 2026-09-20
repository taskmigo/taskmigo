package io.taskmigo.identity.user.application;

import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.Username;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Applies canonical User mutations through a domain-shaped command repository.
@Service
public class DefaultUserCommandService implements UserCommandService {

    private final UserCommandRepository users;

    public DefaultUserCommandService(UserCommandRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
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
    @Transactional
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

        boolean profileChanged = existing.reconcileProfile(emails, firstName, lastName);
        boolean credentialChanged = existing.initializeCredential(initialPasswordHash);
        boolean changed = profileChanged || credentialChanged;
        if (changed) {
            this.users.save(existing);
        }
        return new UserMutationResult(existing.id(), false, changed);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(@Nullable String username) {
        return this.users.findByUsername(Username.of(username));
    }

    @Override
    @Transactional
    public void delete(User user) {
        this.users.delete(user);
    }
}
