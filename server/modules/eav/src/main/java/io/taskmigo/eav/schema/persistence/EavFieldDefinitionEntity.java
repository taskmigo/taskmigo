package io.taskmigo.eav.schema.persistence;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.domain.EavDataType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "eav_field_definitions")
@SuppressWarnings("NotNullFieldNotInitialized")
class EavFieldDefinitionEntity {

    @Id
    UUID id;

    @Column(name = "schema_version_id", nullable = false)
    UUID schemaVersionId;

    @Column(name = "field_id", nullable = false)
    UUID fieldId;

    @Column(name = "field_key", nullable = false, length = 200)
    String key;

    @Column(nullable = false, length = 200)
    String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 16)
    EavDataType dataType;

    @Column(nullable = false)
    boolean required;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "constraints_json", nullable = false, columnDefinition = "jsonb")
    Map<String, Object> constraints = new LinkedHashMap<>();

    protected EavFieldDefinitionEntity() {}

    EavFieldDefinitionEntity(UUID id, UUID schemaVersionId, EavFieldSpec field) {
        this.id = Objects.requireNonNull(id);
        this.schemaVersionId = Objects.requireNonNull(schemaVersionId);
        this.fieldId = field.fieldId();
        this.key = field.key();
        this.label = field.label();
        this.dataType = field.dataType();
        this.required = field.required();
        this.constraints.putAll(field.constraints());
    }

    EavFieldSpec toSpec() {
        return new EavFieldSpec(
            this.fieldId,
            this.key,
            this.label,
            this.dataType,
            this.required,
            Map.copyOf(this.constraints)
        );
    }
}
