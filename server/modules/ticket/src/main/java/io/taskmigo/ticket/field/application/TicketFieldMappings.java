package io.taskmigo.ticket.field.application;

import io.taskmigo.eav.query.EavQueryField;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.ticket.field.application.api.TicketFieldType;

/// Translates Ticket-owned field vocabulary into the internal EAV capability.
public final class TicketFieldMappings {

    private TicketFieldMappings() {}

    public static EavDataType toEavType(TicketFieldType dataType) {
        return switch (dataType) {
            case STRING -> EavDataType.STRING;
            case NUMBER -> EavDataType.NUMBER;
            case BOOLEAN -> EavDataType.BOOLEAN;
            case DATE -> EavDataType.DATE;
            case DATETIME -> EavDataType.DATETIME;
            case UUID -> EavDataType.UUID;
            case ENUM -> EavDataType.ENUM;
        };
    }

    public static TicketFieldType fromEavType(EavDataType dataType) {
        return switch (dataType) {
            case STRING -> TicketFieldType.STRING;
            case NUMBER -> TicketFieldType.NUMBER;
            case BOOLEAN -> TicketFieldType.BOOLEAN;
            case DATE -> TicketFieldType.DATE;
            case DATETIME -> TicketFieldType.DATETIME;
            case UUID -> TicketFieldType.UUID;
            case ENUM -> TicketFieldType.ENUM;
        };
    }

    /// Registered fields are nullable in a heterogeneous Ticket result set.
    public static EavQueryField queryField(TicketFieldRegistry.Entry entry) {
        return EavQueryField.describe(entry.id(), entry.key(), toEavType(entry.dataType()), true);
    }
}
