package io.taskmigo.eav.schema.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "eav_schema_definitions")
@SuppressWarnings("NotNullFieldNotInitialized")
class EavSchemaDefinitionEntity {

    @Id
    UUID id;

    @Column(name = "schema_key", nullable = false, length = 200, unique = true)
    String key;

    @Column(nullable = false, length = 200)
    String name;

    @Nullable
    @Column(name = "current_version_id")
    UUID currentVersionId;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected EavSchemaDefinitionEntity() {}

    EavSchemaDefinitionEntity(UUID id, String key, String name, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.key = Objects.requireNonNull(key);
        this.name = Objects.requireNonNull(name);
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    UUID id() {
        return this.id;
    }

    @Nullable
    UUID currentVersionId() {
        return this.currentVersionId;
    }

    void selectCurrent(@Nullable UUID versionId, Instant now) {
        this.currentVersionId = versionId;
        this.updatedAt = Objects.requireNonNull(now);
    }
}
