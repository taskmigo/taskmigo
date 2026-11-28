package io.taskmigo.ticket.application;

import io.taskmigo.eav.query.EavQueryField;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import io.taskmigo.ticket.field.application.TicketFieldMappings;
import io.taskmigo.ticket.field.application.TicketFieldRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Resolves immutable runtime custom-field snapshots from the shared Ticket registry.
public final class TicketQueryFieldCatalog implements TicketQueryFieldContexts {

    private static final String SNAPSHOT_KEY = TicketQueryFieldCatalog.class.getName() + ".snapshot";

    private final TicketFieldRegistry registry;

    public TicketQueryFieldCatalog(TicketFieldRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    @Override
    public QueryFieldContext snapshot() {
        Map<String, EavQueryField> fields = this.registry
            .findAll()
            .stream()
            .map(TicketFieldMappings::queryField)
            .collect(Collectors.toUnmodifiableMap(EavQueryField::key, Function.identity()));
        return new QueryFieldContext(Map.of(SNAPSHOT_KEY, new Snapshot(fields)));
    }

    /// Extracts the resolved runtime fields from a context, using the global catalog for an empty request-independent context.
    public Map<String, EavQueryField> fields(QueryFieldContext context) {
        Objects.requireNonNull(context);
        Object raw = context.values().get(SNAPSHOT_KEY);
        if (raw == null) {
            return this.fields(this.snapshot());
        }
        if (!(raw instanceof Snapshot snapshot)) {
            throw new IllegalArgumentException("Ticket query field context contains an invalid runtime snapshot");
        }
        return snapshot.fields();
    }

    private record Snapshot(Map<String, EavQueryField> fields) {
        private Snapshot {
            fields = Map.copyOf(Objects.requireNonNull(fields));
        }
    }
}
