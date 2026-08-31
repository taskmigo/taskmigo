package io.taskmigo.user;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Hard-deletes explicitly owned user records for isolated test cleanup.
@Service
public class UserTestDataCleaner {

    private final UserRepository users;

    UserTestDataCleaner(UserRepository users) {
        this.users = users;
    }

    /// Deletes only the user identifiers supplied by the test ownership scope.
    @Transactional
    public void purge(Set<UUID> ids) {
        if (!ids.isEmpty()) this.users.deleteAllByIdInBatch(ids);
    }
}
