package io.taskmigo.group;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Hard-deletes explicitly owned group records for isolated test cleanup.
@Service
public class GroupTestDataCleaner {

    private final GroupRepository groups;

    GroupTestDataCleaner(GroupRepository groups) {
        this.groups = groups;
    }

    /// Deletes only the group identifiers supplied by the test ownership scope.
    @Transactional
    public void purge(Set<UUID> ids) {
        if (!ids.isEmpty()) this.groups.deleteAllByIdInBatch(ids);
    }
}
