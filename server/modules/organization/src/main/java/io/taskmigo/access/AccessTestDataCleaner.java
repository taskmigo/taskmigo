package io.taskmigo.access;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Hard-deletes explicitly owned role records for isolated test cleanup.
@Service
public class AccessTestDataCleaner {

    private final RoleRepository roles;

    AccessTestDataCleaner(RoleRepository roles) {
        this.roles = roles;
    }

    /// Deletes only the role identifiers supplied by the test ownership scope.
    @Transactional
    public void purge(Set<UUID> ids) {
        if (!ids.isEmpty()) this.roles.deleteAllByIdInBatch(ids);
    }
}
