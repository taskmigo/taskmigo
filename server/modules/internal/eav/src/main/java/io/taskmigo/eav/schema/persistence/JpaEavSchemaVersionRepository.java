package io.taskmigo.eav.schema.persistence;

import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface JpaEavSchemaVersionRepository
    extends JpaRepository<EavSchemaVersionEntity, UUID>, JpaSpecificationExecutor<EavSchemaVersionEntity>
{
    Optional<EavSchemaVersionEntity> findByIdAndSchemaDefinitionId(UUID id, UUID schemaDefinitionId);

    Optional<EavSchemaVersionEntity> findTopBySchemaDefinitionIdOrderByVersionDesc(UUID schemaDefinitionId);

    List<EavSchemaVersionEntity> findAllBySchemaDefinitionIdOrderByVersionAsc(UUID schemaDefinitionId);

    List<EavSchemaVersionEntity> findAllBySchemaDefinitionIdAndStatusNot(
        UUID schemaDefinitionId,
        EavSchemaStatus status
    );
}
