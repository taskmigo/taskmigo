package io.taskmigo.ticket.field.application;

import io.taskmigo.eav.query.EavQueryField;
import io.taskmigo.foundation.DomainFailureType;
import io.taskmigo.ticket.field.application.api.RegisteredTicketField;
import io.taskmigo.ticket.field.application.api.TicketFieldException;
import io.taskmigo.ticket.field.application.api.TicketFieldService;
import io.taskmigo.ticket.field.application.api.TicketFieldStatus;
import io.taskmigo.ticket.field.application.api.TicketFieldType;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

/// Manages the lifecycle of shared Ticket field definitions.
@Transactional
public class TicketFieldServiceImpl implements TicketFieldService {

    private final TicketFieldRegistry registry;

    public TicketFieldServiceImpl(TicketFieldRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    @Override
    public RegisteredTicketField create(String key, String label, TicketFieldType dataType) {
        TicketFieldRegistry.Entry entry = new TicketFieldRegistry.Entry(
            UUID.randomUUID(),
            key,
            label,
            dataType,
            TicketFieldStatus.ACTIVE
        );
        if (this.registry.findByKey(key).isPresent()) {
            throw new TicketFieldException(DomainFailureType.CONFLICT, "Ticket field key already exists: " + key);
        }
        this.registry.insert(entry);
        return toInfo(entry);
    }

    @Transactional(readOnly = true)
    @Override
    public RegisteredTicketField get(UUID id) {
        return toInfo(
            this.registry
                .findById(id)
                .orElseThrow(() -> new TicketFieldException(DomainFailureType.NOT_FOUND, "Unknown Ticket field: " + id))
        );
    }

    @Transactional(readOnly = true)
    @Override
    public List<RegisteredTicketField> list() {
        return this.registry
            .findAll()
            .stream()
            .sorted(Comparator.comparing(TicketFieldRegistry.Entry::key))
            .map(TicketFieldServiceImpl::toInfo)
            .toList();
    }

    @Override
    public RegisteredTicketField update(UUID id, @Nullable String label, @Nullable TicketFieldStatus status) {
        TicketFieldRegistry.Entry previous = this.registry
            .lockById(id)
            .orElseThrow(() -> new TicketFieldException(DomainFailureType.NOT_FOUND, "Unknown Ticket field: " + id));
        TicketFieldStatus nextStatus = status == null ? previous.status() : status;
        if (previous.status() == TicketFieldStatus.ARCHIVED && nextStatus != TicketFieldStatus.ARCHIVED) {
            throw new TicketFieldException(
                DomainFailureType.CONFLICT,
                "An archived Ticket field cannot be reactivated"
            );
        }
        TicketFieldRegistry.Entry entry = new TicketFieldRegistry.Entry(
            previous.id(),
            previous.key(),
            label == null ? previous.label() : label,
            previous.dataType(),
            nextStatus
        );
        this.registry.update(entry);
        return toInfo(entry);
    }

    private static RegisteredTicketField toInfo(TicketFieldRegistry.Entry entry) {
        EavQueryField query = TicketFieldMappings.queryField(entry);
        return new RegisteredTicketField(
            entry.id(),
            entry.key(),
            entry.label(),
            entry.dataType(),
            entry.status(),
            query.operators().stream().sorted().toList(),
            query.sortable(),
            query.nullable()
        );
    }
}
