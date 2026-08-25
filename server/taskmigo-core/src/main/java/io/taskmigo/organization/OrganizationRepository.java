package io.taskmigo.organization;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {}
