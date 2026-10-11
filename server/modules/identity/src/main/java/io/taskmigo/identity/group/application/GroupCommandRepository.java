package io.taskmigo.identity.group.application;

import io.taskmigo.identity.group.domain.Group;
import io.taskmigo.identity.group.domain.GroupCode;
import java.util.Optional;

/// Persists canonical Group aggregate state for command use cases.
public interface GroupCommandRepository {
    Optional<Group> findByCode(GroupCode code);

    void save(Group group);

    void delete(Group group);
}
