package io.taskmigo.eav.value.persistence;

import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavValue;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "eav_field_values")
@SuppressWarnings("NotNullFieldNotInitialized")
class EavFieldValueEntity {

    @EmbeddedId
    EavFieldValueId id;

    @Column(name = "schema_version_id", nullable = false)
    UUID schemaVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 16)
    EavDataType dataType;

    @Column(name = "string_value")
    @Nullable
    String stringValue;

    @Column(name = "number_value")
    @Nullable
    BigDecimal numberValue;

    @Column(name = "boolean_value")
    @Nullable
    Boolean booleanValue;

    @Column(name = "date_value")
    @Nullable
    LocalDate dateValue;

    @Column(name = "datetime_value")
    @Nullable
    Instant dateTimeValue;

    @Column(name = "uuid_value")
    @Nullable
    UUID uuidValue;

    @Column(name = "enum_value")
    @Nullable
    String enumValue;

    protected EavFieldValueEntity() {}

    EavFieldValueEntity(UUID schemaInstanceId, UUID schemaVersionId, UUID fieldId, EavValue value) {
        this.id = new EavFieldValueId(schemaInstanceId, fieldId);
        this.schemaVersionId = Objects.requireNonNull(schemaVersionId);
        this.dataType = Objects.requireNonNull(value).dataType();
        switch (value) {
            case EavValue.StringValue string -> this.stringValue = string.value();
            case EavValue.NumberValue number -> this.numberValue = number.value();
            case EavValue.BooleanValue bool -> this.booleanValue = bool.value();
            case EavValue.DateValue date -> this.dateValue = date.value();
            case EavValue.DateTimeValue dateTime -> this.dateTimeValue = dateTime.value();
            case EavValue.UuidValue uuid -> this.uuidValue = uuid.value();
            case EavValue.EnumValue option -> this.enumValue = option.value();
        }
    }

    UUID fieldId() {
        return this.id.fieldId;
    }

    EavValue toValue() {
        return switch (this.dataType) {
            case STRING -> new EavValue.StringValue(Objects.requireNonNull(this.stringValue));
            case NUMBER -> new EavValue.NumberValue(Objects.requireNonNull(this.numberValue));
            case BOOLEAN -> new EavValue.BooleanValue(Objects.requireNonNull(this.booleanValue));
            case DATE -> new EavValue.DateValue(Objects.requireNonNull(this.dateValue));
            case DATETIME -> new EavValue.DateTimeValue(Objects.requireNonNull(this.dateTimeValue));
            case UUID -> new EavValue.UuidValue(Objects.requireNonNull(this.uuidValue));
            case ENUM -> new EavValue.EnumValue(Objects.requireNonNull(this.enumValue));
        };
    }

    @Embeddable
    static class EavFieldValueId implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Column(name = "schema_instance_id", nullable = false)
        UUID schemaInstanceId;

        @Column(name = "field_id", nullable = false)
        UUID fieldId;

        protected EavFieldValueId() {}

        EavFieldValueId(UUID schemaInstanceId, UUID fieldId) {
            this.schemaInstanceId = Objects.requireNonNull(schemaInstanceId);
            this.fieldId = Objects.requireNonNull(fieldId);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof EavFieldValueId value)) {
                return false;
            }
            return this.schemaInstanceId.equals(value.schemaInstanceId) && this.fieldId.equals(value.fieldId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.schemaInstanceId, this.fieldId);
        }
    }
}
