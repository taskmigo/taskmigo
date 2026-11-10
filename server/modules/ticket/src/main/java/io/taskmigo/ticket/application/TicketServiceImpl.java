package io.taskmigo.ticket.application;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.eav.value.application.EavInstanceService;
import io.taskmigo.eav.value.application.EavInstanceView;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.ListQueryCompiler;
import io.taskmigo.query.ListQueryRequest;
import io.taskmigo.query.ListQuerySpec;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.api.TicketInfo;
import io.taskmigo.ticket.application.api.TicketPatch;
import io.taskmigo.ticket.application.api.TicketQuery;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.ticket.domain.Ticket;
import io.taskmigo.ticket.type.application.TicketTypeRepository;
import io.taskmigo.ticket.type.domain.TicketType;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

/// Coordinates Ticket common fields with generic EAV instances pinned to immutable historical schemas.
@Transactional
public class TicketServiceImpl implements TicketService {

    private final TicketTypeRepository ticketTypes;
    private final EavSchemaService schemas;
    private final EavInstanceService instances;
    private final TicketRepository tickets;
    private final TicketQueryRepository queries;
    private final TicketQueryFieldContexts queryFields;
    private final TicketQuery.List querySchema;
    private final ListQueryCompiler queryCompiler;
    private final Clock clock;

    public TicketServiceImpl(
        TicketTypeRepository ticketTypes,
        EavSchemaService schemas,
        EavInstanceService instances,
        TicketRepository tickets,
        TicketQueryRepository queries,
        TicketQueryFieldContexts queryFields,
        TicketQuery.List querySchema,
        ListQueryCompiler queryCompiler,
        Clock clock
    ) {
        this.ticketTypes = Objects.requireNonNull(ticketTypes);
        this.schemas = Objects.requireNonNull(schemas);
        this.instances = Objects.requireNonNull(instances);
        this.tickets = Objects.requireNonNull(tickets);
        this.queries = Objects.requireNonNull(queries);
        this.queryFields = Objects.requireNonNull(queryFields);
        this.querySchema = Objects.requireNonNull(querySchema);
        this.queryCompiler = Objects.requireNonNull(queryCompiler);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public UUID create(UUID ticketTypeId, String title, String status, Map<String, ?> customFields, UUID actorId) {
        Objects.requireNonNull(ticketTypeId);
        Objects.requireNonNull(customFields);
        Objects.requireNonNull(actorId);
        requireNonBlank(title, "Ticket title");
        requireNonBlank(status, "Ticket status");

        TicketType type = this.requireType(ticketTypeId);
        type.requireCreationEligible();
        EavSchemaVersionInfo current = this.schemas
            .current(type.schemaDefinitionId())
            .orElseThrow(() -> new IllegalStateException("Ticket Type has no current published template"));
        if (current.status() != EavSchemaStatus.PUBLISHED) {
            throw new IllegalStateException("Ticket Type current template must be published");
        }

        EavInstanceView instance = this.instances.create(type.schemaDefinitionId(), current.id(), customFields);
        requireOwnedInstance(type, instance);
        if (!instance.schemaVersionId().equals(current.id())) {
            throw new IllegalStateException("EAV instance was not pinned to the validated template version");
        }

        UUID id = UUID.randomUUID();
        this.tickets.save(Ticket.create(id, type.id(), instance.id(), title, status, actorId, this.now()));
        return id;
    }

    @Transactional(readOnly = true)
    @Override
    public OffsetPage<TicketInfo> list(
        int page,
        int perPage,
        @Nullable UUID ticketTypeId,
        ListQueryRequest request,
        ObjectAuthorizationPredicate<TicketInfo> authorization
    ) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(authorization);
        QueryFieldContext context =
            ticketTypeId == null ? this.queryFields.global() : this.queryFields.forType(ticketTypeId);
        ListQuerySpec<TicketInfo> query = this.queryCompiler.compile(this.querySchema, context, request);
        OffsetPage<Ticket> result = this.queries.list(page, perPage, ticketTypeId, query, authorization);
        List<TicketInfo> items = result
            .items()
            .stream()
            .map(ticket -> {
                TicketType type = this.requireType(ticket.ticketTypeId());
                EavInstanceView instance = this.instances.get(ticket.schemaInstanceId());
                requireOwnedInstance(type, instance);
                return toInfo(ticket, instance);
            })
            .toList();
        return new OffsetPage<>(items, result.totalItems(), result.totalPages());
    }

    @Transactional(readOnly = true)
    @Override
    public TicketInfo read(UUID ticketId, ObjectAuthorizationPredicate<TicketInfo> authorization) {
        Ticket ticket = this.requireAuthorized(ticketId, authorization);
        TicketType type = this.requireType(ticket.ticketTypeId());
        EavInstanceView instance = this.instances.get(ticket.schemaInstanceId());
        requireOwnedInstance(type, instance);
        return toInfo(ticket, instance);
    }

    @Override
    public TicketInfo update(UUID ticketId, TicketPatch patch, ObjectAuthorizationPredicate<TicketInfo> authorization) {
        Objects.requireNonNull(patch);
        Ticket ticket = this.requireAuthorized(ticketId, authorization);
        TicketType type = this.requireType(ticket.ticketTypeId());

        if (!patch.hasChanges()) {
            EavInstanceView current = this.instances.get(ticket.schemaInstanceId());
            requireOwnedInstance(type, current);
            return toInfo(ticket, current);
        }

        String title = patch.title() == null ? ticket.title() : patch.title();
        String status = patch.status() == null ? ticket.status() : patch.status();
        Ticket updated = ticket.updateCommonFields(title, status, this.now());

        EavInstanceView instance = patch.customFields().isEmpty()
            ? this.instances.get(ticket.schemaInstanceId())
            : this.instances.patch(ticket.schemaInstanceId(), patch.customFields());
        requireOwnedInstance(type, instance);
        this.tickets.save(updated);
        return toInfo(updated, instance);
    }

    private Ticket requireAuthorized(UUID ticketId, ObjectAuthorizationPredicate<TicketInfo> authorization) {
        Objects.requireNonNull(ticketId);
        Objects.requireNonNull(authorization);
        QueryFieldContext context = this.queryFields.forTicket(ticketId);
        return this.queries
            .findAuthorized(ticketId, authorization, context)
            .orElseThrow(() -> new IllegalArgumentException("Unknown or unauthorized Ticket"));
    }

    private TicketType requireType(UUID id) {
        return this.ticketTypes
            .findById(Objects.requireNonNull(id))
            .orElseThrow(() -> new IllegalArgumentException("Unknown Ticket Type"));
    }

    private static void requireOwnedInstance(TicketType type, EavInstanceView instance) {
        if (!instance.schemaDefinitionId().equals(type.schemaDefinitionId())) {
            throw new IllegalStateException("Ticket schema instance does not belong to its Ticket Type");
        }
    }

    private static TicketInfo toInfo(Ticket ticket, EavInstanceView instance) {
        return new TicketInfo(
            ticket.id(),
            ticket.ticketTypeId(),
            instance.schemaVersionId(),
            ticket.title(),
            ticket.status(),
            ticket.createdBy(),
            ticket.createdAt(),
            ticket.updatedAt(),
            instance.values()
        );
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static void requireNonBlank(String value, String label) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }
}
