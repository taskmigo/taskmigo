package io.taskmigo.acl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AclPolicyRepository extends JpaRepository<AclPolicyEntity, UUID> {
    List<AclPolicyEntity> findAllByOrganizationIdOrderByName(UUID organizationId);

    Optional<AclPolicyEntity> findByOrganizationIdAndName(UUID organizationId, String name);

    void deleteByOrganizationIdAndName(UUID organizationId, String name);
}
