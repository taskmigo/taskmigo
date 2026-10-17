package io.taskmigo.eav.schema.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface JpaEavSchemaDefinitionRepository extends JpaRepository<EavSchemaDefinitionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EavSchemaDefinitionEntity> findFirstById(UUID id);
}
