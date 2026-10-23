package io.taskmigo.eav.value.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaEavFieldValueRepository extends JpaRepository<EavFieldValueEntity, EavFieldValueEntity.EavFieldValueId> {
    List<EavFieldValueEntity> findAllByIdSchemaInstanceId(UUID schemaInstanceId);

    void deleteAllByIdSchemaInstanceId(UUID schemaInstanceId);
}
