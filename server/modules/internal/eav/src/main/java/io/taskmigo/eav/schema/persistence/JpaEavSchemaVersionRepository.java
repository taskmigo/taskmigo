package io.taskmigo.eav.schema.persistence;

import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaEavSchemaVersionRepository extends JpaRepository<EavSchemaVersionEntity, UUID> {
    Optional<EavSchemaVersionEntity> findByIdAndSchemaDefinitionId(UUID id, UUID schemaDefinitionId);

    Optional<EavSchemaVersionEntity> findTopBySchemaDefinitionIdOrderByVersionDesc(UUID schemaDefinitionId);

    List<EavSchemaVersionEntity> findAllBySchemaDefinitionIdOrderByVersionAsc(UUID schemaDefinitionId);

    List<EavSchemaVersionEntity> findAllBySchemaDefinitionIdAndStatusNot(
        UUID schemaDefinitionId,
        EavSchemaStatus status
    );
}
