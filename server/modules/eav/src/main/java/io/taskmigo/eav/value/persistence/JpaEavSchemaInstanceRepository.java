package io.taskmigo.eav.value.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaEavSchemaInstanceRepository extends JpaRepository<EavSchemaInstanceEntity, UUID> {}
