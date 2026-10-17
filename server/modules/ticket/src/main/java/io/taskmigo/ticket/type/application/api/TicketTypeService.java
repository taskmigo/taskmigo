package io.taskmigo.ticket.type.application.api;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.ticket.type.domain.TicketTypeStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Defines Ticket Type metadata and versioned custom-template use cases.
public interface TicketTypeService {
    UUID create(String key, String name);

    TicketTypeInfo get(UUID id);

    List<TicketTypeInfo> list();

    TicketTypeInfo update(UUID id, String name, TicketTypeStatus status);

    EavSchemaVersionInfo createTemplate(UUID ticketTypeId);

    EavSchemaVersionInfo replaceTemplate(UUID ticketTypeId, UUID versionId, List<EavFieldSpec> fields);

    EavSchemaVersionInfo publishTemplate(UUID ticketTypeId, UUID versionId);

    EavSchemaVersionInfo retireTemplate(UUID ticketTypeId, UUID versionId);

    List<EavSchemaVersionInfo> listTemplates(UUID ticketTypeId);

    EavSchemaVersionInfo getTemplate(UUID ticketTypeId, UUID versionId);

    Optional<EavSchemaVersionInfo> currentTemplate(UUID ticketTypeId);
}
