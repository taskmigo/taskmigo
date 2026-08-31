package io.taskmigo.acl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AclPolicyRepository extends JpaRepository<AclPolicyEntity, UUID> {
    List<AclPolicyEntity> findAllByOriginOrderByName(String origin);

    List<AclPolicyEntity> findAllByOriginAndOrganizationIdOrderByName(String origin, UUID organizationId);

    Optional<AclPolicyEntity> findByOriginAndOrganizationIdAndName(String origin, UUID organizationId, String name);

    void deleteByOriginAndOrganizationIdAndName(String origin, UUID organizationId, String name);
}
