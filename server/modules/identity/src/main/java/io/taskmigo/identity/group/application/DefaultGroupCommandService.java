package io.taskmigo.identity.group.application;

import io.taskmigo.identity.group.domain.Group;
import io.taskmigo.identity.group.domain.GroupCode;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Applies canonical Group profile mutations through a domain-shaped command repository.
@Service
public class DefaultGroupCommandService implements GroupCommandService {

    private final GroupCommandRepository groups;

    public DefaultGroupCommandService(GroupCommandRepository groups) {
        this.groups = groups;
    }

    @Override
    @Transactional
    public UUID createRuntime(@Nullable String code, @Nullable String displayName, @Nullable String description) {
        Group group = Group.create(UUID.randomUUID(), code, displayName, description);
        this.groups.save(group);
        return group.id();
    }

    @Override
    @Transactional
    public GroupMutationResult reconcileManaged(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description
    ) {
        GroupCode normalizedCode = GroupCode.of(code);
        Group existing = this.groups.findByCode(normalizedCode).orElse(null);
        if (existing == null) {
            Group created = Group.create(UUID.randomUUID(), normalizedCode.value(), displayName, description);
            this.groups.save(created);
            return new GroupMutationResult(created.id(), true, true);
        }

        boolean changed = existing.reconcileProfile(displayName, description);
        if (changed) {
            this.groups.save(existing);
        }
        return new GroupMutationResult(existing.id(), false, changed);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Group> findByCode(@Nullable String code) {
        return this.groups.findByCode(GroupCode.of(code));
    }

    @Override
    @Transactional
    public void delete(Group group) {
        this.groups.delete(group);
    }
}
