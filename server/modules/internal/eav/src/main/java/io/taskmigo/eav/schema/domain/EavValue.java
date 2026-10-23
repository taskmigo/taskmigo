package io.taskmigo.eav.schema.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/// Represents one normalized typed value accepted by an EAV field definition.
public sealed interface EavValue {
    /// Returns the schema type represented by this value.
    EavDataType dataType();

    record StringValue(String value) implements EavValue {
        public StringValue {
            Objects.requireNonNull(value);
        }

        @Override
        public EavDataType dataType() {
            return EavDataType.STRING;
        }
    }

    record NumberValue(BigDecimal value) implements EavValue {
        public NumberValue {
            Objects.requireNonNull(value);
        }

        @Override
        public EavDataType dataType() {
            return EavDataType.NUMBER;
        }
    }

    record BooleanValue(boolean value) implements EavValue {
        @Override
        public EavDataType dataType() {
            return EavDataType.BOOLEAN;
        }
    }

    record DateValue(LocalDate value) implements EavValue {
        public DateValue {
            Objects.requireNonNull(value);
        }

        @Override
        public EavDataType dataType() {
            return EavDataType.DATE;
        }
    }

    record DateTimeValue(Instant value) implements EavValue {
        public DateTimeValue {
            Objects.requireNonNull(value);
        }

        @Override
        public EavDataType dataType() {
            return EavDataType.DATETIME;
        }
    }

    record UuidValue(UUID value) implements EavValue {
        public UuidValue {
            Objects.requireNonNull(value);
        }

        @Override
        public EavDataType dataType() {
            return EavDataType.UUID;
        }
    }

    record EnumValue(String value) implements EavValue {
        public EnumValue {
            Objects.requireNonNull(value);
        }

        @Override
        public EavDataType dataType() {
            return EavDataType.ENUM;
        }
    }
}
