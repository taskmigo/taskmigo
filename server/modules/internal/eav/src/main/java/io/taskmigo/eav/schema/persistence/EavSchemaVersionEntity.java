package io.taskmigo.eav.schema.persistence;

import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "eav_schema_versions")
@SuppressWarnings("NotNullFieldNotInitialized")
class EavSchemaVersionEntity {

    @Id
    UUID id;

    @Column(name = "schema_definition_id", nullable = false)
    UUID schemaDefinitionId;

    @Column(nullable = false)
    long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    EavSchemaStatus status;

    @Nullable
    @Column(name = "published_at")
    Instant publishedAt;

    @Nullable
    @Column(name = "retired_at")
    Instant retiredAt;

    protected EavSchemaVersionEntity() {}

    EavSchemaVersionEntity(
        UUID id,
        UUID schemaDefinitionId,
        long version,
        EavSchemaStatus status,
        @Nullable Instant publishedAt,
        @Nullable Instant retiredAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.schemaDefinitionId = Objects.requireNonNull(schemaDefinitionId);
        this.version = version;
        this.status = Objects.requireNonNull(status);
        this.publishedAt = publishedAt;
        this.retiredAt = retiredAt;
    }

    UUID id() {
        return this.id;
    }

    UUID schemaDefinitionId() {
        return this.schemaDefinitionId;
    }

    long version() {
        return this.version;
    }

    EavSchemaStatus status() {
        return this.status;
    }

    @Nullable
    Instant publishedAt() {
        return this.publishedAt;
    }

    @Nullable
    Instant retiredAt() {
        return this.retiredAt;
    }

    void updateLifecycle(EavSchemaStatus status, @Nullable Instant publishedAt, @Nullable Instant retiredAt) {
        this.status = Objects.requireNonNull(status);
        this.publishedAt = publishedAt;
        this.retiredAt = retiredAt;
    }
}
