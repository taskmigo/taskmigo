package io.taskmigo.eav.schema.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Normalizes raw custom-field input and enforces the field's declared type and constraints.
public final class EavValueValidator {

    /// Validates one raw value against its field definition.
    ///
    /// @return the normalized typed value, or empty when an optional field is missing
    public Optional<EavValue> validate(EavFieldDefinition field, @Nullable Object raw) {
        Objects.requireNonNull(field);
        if (raw == null) {
            if (field.required()) {
                throw new IllegalArgumentException("EAV field '" + field.key() + "' is required");
            }
            return Optional.empty();
        }
        EavValue value = switch (field.dataType()) {
            case STRING -> this.stringValue(field, raw);
            case NUMBER -> this.numberValue(field, raw);
            case BOOLEAN -> this.booleanValue(field, raw);
            case DATE -> this.dateValue(field, raw);
            case DATETIME -> this.dateTimeValue(field, raw);
            case UUID -> this.uuidValue(field, raw);
            case ENUM -> this.enumValue(field, raw);
        };
        return Optional.of(value);
    }

    private EavValue.StringValue stringValue(EavFieldDefinition field, Object raw) {
        if (!(raw instanceof String value)) {
            throw typeMismatch(field);
        }
        Integer minimum = EavFieldDefinition.integerConstraint(field.constraints(), "minLength");
        Integer maximum = EavFieldDefinition.integerConstraint(field.constraints(), "maxLength");
        if (minimum != null && value.length() < minimum) {
            throw new IllegalArgumentException("EAV field '" + field.key() + "' is shorter than minLength");
        }
        if (maximum != null && value.length() > maximum) {
            throw new IllegalArgumentException("EAV field '" + field.key() + "' exceeds maxLength");
        }
        return new EavValue.StringValue(value);
    }

    private EavValue.NumberValue numberValue(EavFieldDefinition field, Object raw) {
        if (!(raw instanceof Number number)) {
            throw typeMismatch(field);
        }
        BigDecimal value = new BigDecimal(number.toString());
        BigDecimal minimum = EavFieldDefinition.decimalConstraint(field.constraints(), "minimum");
        BigDecimal maximum = EavFieldDefinition.decimalConstraint(field.constraints(), "maximum");
        if (minimum != null && value.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("EAV field '" + field.key() + "' is below minimum");
        }
        if (maximum != null && value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("EAV field '" + field.key() + "' exceeds maximum");
        }
        return new EavValue.NumberValue(value);
    }

    private EavValue.BooleanValue booleanValue(EavFieldDefinition field, Object raw) {
        if (!(raw instanceof Boolean value)) {
            throw typeMismatch(field);
        }
        return new EavValue.BooleanValue(value);
    }

    private EavValue.DateValue dateValue(EavFieldDefinition field, Object raw) {
        LocalDate value;
        try {
            value = switch (raw) {
                case LocalDate date -> date;
                case String text -> LocalDate.parse(text);
                default -> throw typeMismatch(field);
            };
        } catch (DateTimeParseException exception) {
            throw typeMismatch(field, exception);
        }
        LocalDate minimum = EavFieldDefinition.dateConstraint(field.constraints(), "minimum");
        LocalDate maximum = EavFieldDefinition.dateConstraint(field.constraints(), "maximum");
        if (minimum != null && value.isBefore(minimum)) {
            throw new IllegalArgumentException("EAV field '" + field.key() + "' is before minimum");
        }
        if (maximum != null && value.isAfter(maximum)) {
            throw new IllegalArgumentException("EAV field '" + field.key() + "' is after maximum");
        }
        return new EavValue.DateValue(value);
    }

    private EavValue.DateTimeValue dateTimeValue(EavFieldDefinition field, Object raw) {
        Instant value;
        try {
            value = switch (raw) {
                case Instant instant -> instant;
                case String text -> Instant.parse(text);
                default -> throw typeMismatch(field);
            };
        } catch (DateTimeParseException exception) {
            throw typeMismatch(field, exception);
        }
        Instant minimum = EavFieldDefinition.instantConstraint(field.constraints(), "minimum");
        Instant maximum = EavFieldDefinition.instantConstraint(field.constraints(), "maximum");
        if (minimum != null && value.isBefore(minimum)) {
            throw new IllegalArgumentException("EAV field '" + field.key() + "' is before minimum");
        }
        if (maximum != null && value.isAfter(maximum)) {
            throw new IllegalArgumentException("EAV field '" + field.key() + "' is after maximum");
        }
        return new EavValue.DateTimeValue(value);
    }

    private EavValue.UuidValue uuidValue(EavFieldDefinition field, Object raw) {
        try {
            UUID value = switch (raw) {
                case UUID uuid -> uuid;
                case String text -> UUID.fromString(text);
                default -> throw typeMismatch(field);
            };
            return new EavValue.UuidValue(value);
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Value for EAV field")) {
                throw exception;
            }
            throw typeMismatch(field, exception);
        }
    }

    private EavValue.EnumValue enumValue(EavFieldDefinition field, Object raw) {
        if (!(raw instanceof String value)) {
            throw typeMismatch(field);
        }
        Object optionsRaw = field.constraints().get("options");
        if (!(optionsRaw instanceof Collection<?> options) || !options.contains(value)) {
            throw new IllegalArgumentException(
                "Value for EAV field '" + field.key() + "' is not a declared ENUM option"
            );
        }
        return new EavValue.EnumValue(value);
    }

    private static IllegalArgumentException typeMismatch(EavFieldDefinition field) {
        return new IllegalArgumentException("Value for EAV field '" + field.key() + "' must be " + field.dataType());
    }

    private static IllegalArgumentException typeMismatch(EavFieldDefinition field, RuntimeException cause) {
        return new IllegalArgumentException(
            "Value for EAV field '" + field.key() + "' must be " + field.dataType(),
            cause
        );
    }
}
