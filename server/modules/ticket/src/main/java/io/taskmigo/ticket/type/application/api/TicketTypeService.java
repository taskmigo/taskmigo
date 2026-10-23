package io.taskmigo.ticket.type.application.api;

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

    TicketTemplateInfo createTemplate(UUID ticketTypeId);

    TicketTemplateInfo replaceTemplate(UUID ticketTypeId, UUID versionId, List<TicketFieldSpec> fields);

    TicketTemplateInfo publishTemplate(UUID ticketTypeId, UUID versionId);

    TicketTemplateInfo retireTemplate(UUID ticketTypeId, UUID versionId);

    List<TicketTemplateInfo> listTemplates(UUID ticketTypeId);

    TicketTemplateInfo getTemplate(UUID ticketTypeId, UUID versionId);

    Optional<TicketTemplateInfo> currentTemplate(UUID ticketTypeId);
}
