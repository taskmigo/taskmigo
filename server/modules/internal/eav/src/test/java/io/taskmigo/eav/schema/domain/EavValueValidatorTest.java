package io.taskmigo.eav.schema.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EavValueValidatorTest {

    private final EavValueValidator validator = new EavValueValidator();

    @Test
    @DisplayName("treats missing optional values as absent and rejects missing required values")
    void shouldTreatMissingOptionalValueAsAbsentAndRejectMissingRequiredValue() {
        assertThat(this.validator.validate(field(EavDataType.STRING, false, Map.of()), null)).isEmpty();
        assertThatThrownBy(() -> this.validator.validate(field(EavDataType.STRING, true, Map.of()), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("required");
    }

    @Test
    @DisplayName("validates STRING minimum and maximum length constraints")
    void shouldValidateStringLengthConstraints() {
        EavFieldDefinition field = field(EavDataType.STRING, false, Map.of("minLength", 2, "maxLength", 4));

        assertThat(this.validator.validate(field, "abc")).contains(new EavValue.StringValue("abc"));
        assertThatThrownBy(() -> this.validator.validate(field, "a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> this.validator.validate(field, "abcde")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validates NUMBER ranges using numeric rather than lexical semantics")
    void shouldValidateNumberRangeUsingNumericSemantics() {
        EavFieldDefinition field = field(EavDataType.NUMBER, false, Map.of("minimum", 2, "maximum", 10));

        assertThat(this.validator.validate(field, 3)).contains(new EavValue.NumberValue(new BigDecimal("3")));
        assertThatThrownBy(() -> this.validator.validate(field, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> this.validator.validate(field, 11)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> this.validator.validate(field, "3"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NUMBER");
    }

    @Test
    @DisplayName("validates DATE and DATETIME ranges chronologically")
    void shouldValidateDateAndDateTimeRanges() {
        EavFieldDefinition date = field(
            EavDataType.DATE,
            false,
            Map.of("minimum", "2026-10-10", "maximum", "2026-10-20")
        );
        EavFieldDefinition dateTime = field(
            EavDataType.DATETIME,
            false,
            Map.of("minimum", "2026-10-10T00:00:00Z", "maximum", "2026-10-20T00:00:00Z")
        );

        assertThat(this.validator.validate(date, "2026-10-15")).contains(
            new EavValue.DateValue(LocalDate.parse("2026-10-15"))
        );
        assertThat(this.validator.validate(dateTime, "2026-10-15T12:30:00Z")).contains(
            new EavValue.DateTimeValue(Instant.parse("2026-10-15T12:30:00Z"))
        );
        assertThatThrownBy(() -> this.validator.validate(date, "2026-10-01")).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() -> this.validator.validate(dateTime, "2026-10-21T00:00:00Z")).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    @DisplayName("uses stable ENUM option keys instead of display labels")
    void shouldUseStableEnumKeysRatherThanDisplayLabels() {
        EavFieldDefinition field = field(EavDataType.ENUM, false, Map.of("options", List.of("HIGH", "LOW")));

        assertThat(this.validator.validate(field, "HIGH")).contains(new EavValue.EnumValue("HIGH"));
        assertThatThrownBy(() -> this.validator.validate(field, "High"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ENUM");
    }

    @Test
    @DisplayName("normalizes BOOLEAN UUID DATE and DATETIME inputs to typed EAV values")
    void shouldNormalizeBooleanUuidAndAlreadyTypedTemporalValues() {
        UUID id = UUID.randomUUID();
        LocalDate date = LocalDate.parse("2026-10-15");
        Instant timestamp = Instant.parse("2026-10-15T12:30:00Z");

        assertThat(this.validator.validate(field(EavDataType.BOOLEAN, false, Map.of()), true)).contains(
            new EavValue.BooleanValue(true)
        );
        assertThat(this.validator.validate(field(EavDataType.UUID, false, Map.of()), id.toString())).contains(
            new EavValue.UuidValue(id)
        );
        assertThat(this.validator.validate(field(EavDataType.DATE, false, Map.of()), date)).contains(
            new EavValue.DateValue(date)
        );
        assertThat(this.validator.validate(field(EavDataType.DATETIME, false, Map.of()), timestamp)).contains(
            new EavValue.DateTimeValue(timestamp)
        );
    }

    private static EavFieldDefinition field(EavDataType type, boolean required, Map<String, Object> constraints) {
        return new EavFieldDefinition(UUID.randomUUID(), "field", "Field", type, required, constraints);
    }
}
