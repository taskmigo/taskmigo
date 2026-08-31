package io.taskmigo.organization;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Hard-deletes explicitly owned organization records for isolated test cleanup.
@Service
public class OrganizationTestDataCleaner {

    private final OrganizationRepository organizations;

    OrganizationTestDataCleaner(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    /// Deletes only the organization identifiers supplied by the test ownership scope.
    @Transactional
    public void purge(Set<UUID> ids) {
        if (!ids.isEmpty()) this.organizations.deleteAllByIdInBatch(ids);
    }
}
