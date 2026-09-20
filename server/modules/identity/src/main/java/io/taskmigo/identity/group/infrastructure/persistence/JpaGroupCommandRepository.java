package io.taskmigo.identity.group.infrastructure.persistence;

import io.taskmigo.identity.group.application.GroupCommandRepository;
import io.taskmigo.identity.group.domain.Group;
import io.taskmigo.identity.group.domain.GroupCode;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/// Adapts canonical Group aggregate persistence to JPA without owning hierarchy or membership state.
@Repository
public class JpaGroupCommandRepository implements GroupCommandRepository {

    private final JpaGroupRepository groups;

    JpaGroupCommandRepository(JpaGroupRepository groups) {
        this.groups = groups;
    }

    @Override
    public Optional<Group> findByCode(GroupCode code) {
        return this.groups.findByCode(code.value()).map(GroupEntity::toDomain);
    }

    @Override
    public void save(Group group) {
        GroupEntity existing = this.groups.findById(group.id()).orElse(null);
        if (existing == null) {
            this.groups.saveAndFlush(GroupEntity.from(group));
            return;
        }
        existing.updateProfile(group.profile().displayName(), group.profile().description());
        this.groups.flush();
    }

    @Override
    public void delete(Group group) {
        this.groups.deleteById(group.id());
        this.groups.flush();
    }
}
