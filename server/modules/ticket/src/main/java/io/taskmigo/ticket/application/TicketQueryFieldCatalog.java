package io.taskmigo.ticket.application;

import io.taskmigo.eav.query.EavQueryField;
import io.taskmigo.eav.query.EavQueryFieldCatalog;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.value.application.EavInstanceService;
import io.taskmigo.eav.value.application.EavInstanceView;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import io.taskmigo.ticket.domain.Ticket;
import io.taskmigo.ticket.type.application.TicketTypeRepository;
import io.taskmigo.ticket.type.domain.TicketType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/// Resolves immutable runtime custom-field snapshots for Ticket query operations.
public final class TicketQueryFieldCatalog implements TicketQueryFieldContexts {

    private static final String SNAPSHOT_KEY = TicketQueryFieldCatalog.class.getName() + ".snapshot";

    private final EavQueryFieldCatalog eavFields;
    private final EavSchemaService schemas;
    private final EavInstanceService instances;
    private final TicketTypeRepository ticketTypes;
    private final TicketRepository tickets;

    /// Creates the Ticket runtime-field catalog from generic EAV metadata and Ticket ownership repositories.
    public TicketQueryFieldCatalog(
        EavQueryFieldCatalog eavFields,
        EavSchemaService schemas,
        EavInstanceService instances,
        TicketTypeRepository ticketTypes,
        TicketRepository tickets
    ) {
        this.eavFields = Objects.requireNonNull(eavFields);
        this.schemas = Objects.requireNonNull(schemas);
        this.instances = Objects.requireNonNull(instances);
        this.ticketTypes = Objects.requireNonNull(ticketTypes);
        this.tickets = Objects.requireNonNull(tickets);
    }

    /// Returns the request-independent snapshot containing only globally unambiguous custom fields.
    @Override
    public QueryFieldContext global() {
        List<EavSchemaVersionInfo> versions = new ArrayList<>();
        for (TicketType type : this.ticketTypes.findAll()) {
            versions.addAll(this.schemas.listVersions(type.schemaDefinitionId()));
        }
        return snapshot(this.eavFields.resolve(versions));
    }

    /// Returns the runtime-field snapshot narrowed to one Ticket Type's complete published/retired history.
    @Override
    public QueryFieldContext forType(UUID ticketTypeId) {
        TicketType type = this.ticketTypes
            .findById(Objects.requireNonNull(ticketTypeId))
            .orElseThrow(() -> new IllegalArgumentException("Unknown Ticket Type"));
        return snapshot(this.eavFields.resolve(this.schemas.listVersions(type.schemaDefinitionId())));
    }

    /// Returns the exact historical runtime-field snapshot pinned by a Ticket, or the safe global snapshot when absent.
    @Override
    public QueryFieldContext forTicket(UUID ticketId) {
        Optional<Ticket> ticket = this.tickets.findById(Objects.requireNonNull(ticketId));
        if (ticket.isEmpty()) {
            return this.global();
        }
        EavInstanceView instance = this.instances.get(ticket.orElseThrow().schemaInstanceId());
        EavSchemaVersionInfo version = this.schemas.version(instance.schemaDefinitionId(), instance.schemaVersionId());
        return snapshot(this.eavFields.resolve(List.of(version)));
    }

    /// Extracts the resolved runtime fields from a context, using the global catalog for an empty request-independent context.
    public Map<String, EavQueryField> fields(QueryFieldContext context) {
        Objects.requireNonNull(context);
        Object raw = context.values().get(SNAPSHOT_KEY);
        if (raw == null) {
            return this.fields(this.global());
        }
        if (!(raw instanceof Snapshot snapshot)) {
            throw new IllegalArgumentException("Ticket query field context contains an invalid runtime snapshot");
        }
        return snapshot.fields();
    }

    private static QueryFieldContext snapshot(Map<String, EavQueryField> fields) {
        return new QueryFieldContext(Map.of(SNAPSHOT_KEY, new Snapshot(fields)));
    }

    private record Snapshot(Map<String, EavQueryField> fields) {
        private Snapshot {
            fields = Map.copyOf(Objects.requireNonNull(fields));
        }
    }
}
