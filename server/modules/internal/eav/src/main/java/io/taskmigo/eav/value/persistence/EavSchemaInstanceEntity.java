package io.taskmigo.eav.value.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "eav_schema_instances")
@SuppressWarnings("NotNullFieldNotInitialized")
class EavSchemaInstanceEntity {

    @Id
    UUID id;

    @Column(name = "schema_definition_id", nullable = false)
    UUID schemaDefinitionId;

    @Column(name = "schema_version_id", nullable = false)
    UUID schemaVersionId;

    protected EavSchemaInstanceEntity() {}

    EavSchemaInstanceEntity(UUID id, UUID schemaDefinitionId, UUID schemaVersionId) {
        this.id = Objects.requireNonNull(id);
        this.schemaDefinitionId = Objects.requireNonNull(schemaDefinitionId);
        this.schemaVersionId = Objects.requireNonNull(schemaVersionId);
    }

    UUID id() {
        return this.id;
    }

    UUID schemaDefinitionId() {
        return this.schemaDefinitionId;
    }

    UUID schemaVersionId() {
        return this.schemaVersionId;
    }
}
