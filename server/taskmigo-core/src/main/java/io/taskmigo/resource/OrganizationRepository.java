package io.taskmigo.resource;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {}
