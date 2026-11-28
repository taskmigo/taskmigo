package io.taskmigo.ticket.type.application.api;

import io.taskmigo.ticket.type.domain.TicketTypeStatus;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines Ticket Type metadata and versioned custom-template use cases.
public interface TicketTypeService {
    TicketTypeInfo create(String key, String name, List<TicketFieldSpec> fields);

    TicketTypeInfo get(UUID id);

    List<TicketTypeInfo> list();

    TicketTypeInfo update(UUID id, @Nullable String name, @Nullable TicketTypeStatus status);

    TicketTemplateInfo createTemplate(UUID ticketTypeId, UUID expectedTemplateVersionId, List<TicketFieldSpec> fields);

    List<TicketTemplateInfo> listTemplates(UUID ticketTypeId);

    TicketTemplateInfo getTemplate(UUID ticketTypeId, UUID versionId);
}
