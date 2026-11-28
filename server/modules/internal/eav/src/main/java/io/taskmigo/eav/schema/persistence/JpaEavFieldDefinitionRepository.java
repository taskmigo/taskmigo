package io.taskmigo.eav.schema.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaEavFieldDefinitionRepository extends JpaRepository<EavFieldDefinitionEntity, UUID> {
    List<EavFieldDefinitionEntity> findAllBySchemaVersionIdOrderByKey(UUID schemaVersionId);

    List<EavFieldDefinitionEntity> findAllBySchemaVersionIdInOrderByKey(Collection<UUID> schemaVersionIds);

    Optional<EavFieldDefinitionEntity> findFirstBySchemaVersionIdInAndFieldId(
        Collection<UUID> schemaVersionIds,
        UUID fieldId
    );

    void deleteAllBySchemaVersionId(UUID schemaVersionId);
}
