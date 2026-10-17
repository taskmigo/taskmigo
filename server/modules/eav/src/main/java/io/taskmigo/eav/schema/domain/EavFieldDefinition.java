package io.taskmigo.eav.schema.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines one logical field inside an immutable published EAV schema revision.
public record EavFieldDefinition(
    UUID fieldId,
    String key,
    String label,
    EavDataType dataType,
    boolean required,
    Map<String, Object> constraints
) {
    public EavFieldDefinition {
        Objects.requireNonNull(fieldId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(label);
        Objects.requireNonNull(dataType);
        constraints = Map.copyOf(Objects.requireNonNull(constraints));
        if (key.isBlank()) {
            throw new IllegalArgumentException("EAV field key must not be blank");
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("EAV field label must not be blank");
        }
        for (String name : constraints.keySet()) {
            if (!dataType.supportsConstraint(name)) {
                throw new IllegalArgumentException("Unsupported " + dataType + " constraint: " + name);
            }
        }
        validateConstraints(dataType, constraints);
    }

    private static void validateConstraints(EavDataType type, Map<String, Object> constraints) {
        switch (type) {
            case STRING -> validateLengthRange(constraints);
            case NUMBER -> validateNumberRange(constraints);
            case DATE -> validateDateRange(constraints);
            case DATETIME -> validateDateTimeRange(constraints);
            case ENUM -> validateEnumOptions(constraints);
            case BOOLEAN, UUID -> {
            }
        }
    }

    private static void validateLengthRange(Map<String, Object> constraints) {
        Integer minimum = integerConstraint(constraints, "minLength");
        Integer maximum = integerConstraint(constraints, "maxLength");
        if ((minimum != null && minimum < 0) || (maximum != null && maximum < 0)) {
            throw new IllegalArgumentException("STRING length constraints must be non-negative");
        }
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new IllegalArgumentException("STRING minLength must not exceed maxLength");
        }
    }

    private static void validateNumberRange(Map<String, Object> constraints) {
        BigDecimal minimum = decimalConstraint(constraints, "minimum");
        BigDecimal maximum = decimalConstraint(constraints, "maximum");
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("NUMBER minimum must not exceed maximum");
        }
    }

    private static void validateDateRange(Map<String, Object> constraints) {
        LocalDate minimum = dateConstraint(constraints, "minimum");
        LocalDate maximum = dateConstraint(constraints, "maximum");
        if (minimum != null && maximum != null && minimum.isAfter(maximum)) {
            throw new IllegalArgumentException("DATE minimum must not exceed maximum");
        }
    }

    private static void validateDateTimeRange(Map<String, Object> constraints) {
        Instant minimum = instantConstraint(constraints, "minimum");
        Instant maximum = instantConstraint(constraints, "maximum");
        if (minimum != null && maximum != null && minimum.isAfter(maximum)) {
            throw new IllegalArgumentException("DATETIME minimum must not exceed maximum");
        }
    }

    private static void validateEnumOptions(Map<String, Object> constraints) {
        Object raw = constraints.get("options");
        if (raw == null) {
            return;
        }
        if (!(raw instanceof Collection<?> options) || options.isEmpty()) {
            throw new IllegalArgumentException("ENUM options constraint must be a non-empty collection");
        }
        Set<String> unique = new HashSet<>();
        for (Object option : options) {
            if (!(option instanceof String key) || key.isBlank() || !unique.add(key)) {
                throw new IllegalArgumentException("ENUM options must contain unique non-blank stable keys");
            }
        }
    }

    static @Nullable Integer integerConstraint(Map<String, Object> constraints, String name) {
        Object raw = constraints.get(name);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(name + " constraint must be numeric");
        }
        int value = number.intValue();
        if (BigDecimal.valueOf(value).compareTo(new BigDecimal(number.toString())) != 0) {
            throw new IllegalArgumentException(name + " constraint must be an integer");
        }
        return value;
    }

    static @Nullable BigDecimal decimalConstraint(Map<String, Object> constraints, String name) {
        Object raw = constraints.get(name);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(name + " constraint must be numeric");
        }
        return new BigDecimal(number.toString());
    }

    static @Nullable LocalDate dateConstraint(Map<String, Object> constraints, String name) {
        Object raw = constraints.get(name);
        if (raw == null) {
            return null;
        }
        try {
            return switch (raw) {
                case LocalDate date -> date;
                case String text -> LocalDate.parse(text);
                default -> throw new IllegalArgumentException(name + " constraint must be an ISO date");
            };
        } catch (RuntimeException exception) {
            if (
                exception instanceof IllegalArgumentException invalid &&
                invalid.getMessage() != null &&
                invalid.getMessage().contains("constraint")
            ) {
                throw invalid;
            }
            throw new IllegalArgumentException(name + " constraint must be an ISO date", exception);
        }
    }

    static @Nullable Instant instantConstraint(Map<String, Object> constraints, String name) {
        Object raw = constraints.get(name);
        if (raw == null) {
            return null;
        }
        try {
            return switch (raw) {
                case Instant instant -> instant;
                case String text -> Instant.parse(text);
                default -> throw new IllegalArgumentException(name + " constraint must be an ISO instant");
            };
        } catch (RuntimeException exception) {
            if (
                exception instanceof IllegalArgumentException invalid &&
                invalid.getMessage() != null &&
                invalid.getMessage().contains("constraint")
            ) {
                throw invalid;
            }
            throw new IllegalArgumentException(name + " constraint must be an ISO instant", exception);
        }
    }
}
