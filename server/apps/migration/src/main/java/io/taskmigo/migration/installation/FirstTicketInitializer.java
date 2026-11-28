package io.taskmigo.migration.installation;

import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.ticket.field.application.api.TicketFieldService;
import io.taskmigo.ticket.field.application.api.TicketFieldType;
import io.taskmigo.ticket.type.application.api.TicketFieldSpec;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// Creates ordinary example data only during the first successful managed installation.
final class FirstTicketInitializer {

    private static final String DESCRIPTION = """
    Welcome to Taskmigo!

    Your workspace is ready. This is your first ticket, created automatically during installation.

    Feel free to edit this ticket, explore custom fields, create your own ticket types, and start organizing your work.

    Happy tasking!
    """.stripTrailing();

    private final TicketFieldService fields;
    private final TicketTypeService types;
    private final TicketService tickets;

    FirstTicketInitializer(TicketFieldService fields, TicketTypeService types, TicketService tickets) {
        this.fields = fields;
        this.types = types;
        this.tickets = tickets;
    }

    void initialize(UUID systemUserId) {
        var field = this.fields.create("description", "Description", TicketFieldType.STRING);
        var type = this.types.create("task", "Task", List.of(new TicketFieldSpec(field.id(), null, false, Map.of())));
        this.tickets.create(
            type.id(),
            "Hello World! 👋",
            "OPEN",
            Map.of("description", DESCRIPTION),
            systemUserId,
            type.currentTemplate().id()
        );
    }
}
