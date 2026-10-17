package io.taskmigo.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.eav.query.EavQueryField;
import io.taskmigo.eav.query.EavQueryFieldCatalog;
import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.eav.value.application.EavInstanceService;
import io.taskmigo.eav.value.application.EavInstanceView;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.domain.Ticket;
import io.taskmigo.ticket.type.application.TicketTypeRepository;
import io.taskmigo.ticket.type.domain.TicketType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TicketQueryFieldCatalogTest {

    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

    @Test
    @DisplayName("global catalog fails closed for cross-type key ambiguity while type scope remains available")
    void shouldResolveGlobalAndTypeScopedFields() {
        TicketTypeRepository ticketTypes = mock(TicketTypeRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        EavSchemaService schemas = mock(EavSchemaService.class);
        EavInstanceService instances = mock(EavInstanceService.class);
        UUID firstTypeId = UUID.randomUUID();
        UUID secondTypeId = UUID.randomUUID();
        UUID firstDefinitionId = UUID.randomUUID();
        UUID secondDefinitionId = UUID.randomUUID();
        TicketType firstType = TicketType.active(firstTypeId, "incident", "Incident", firstDefinitionId, NOW);
        TicketType secondType = TicketType.active(secondTypeId, "request", "Request", secondDefinitionId, NOW);
        EavSchemaVersionInfo first = version(
            firstDefinitionId,
            1,
            EavSchemaStatus.PUBLISHED,
            List.of(field(UUID.randomUUID(), "severity", EavDataType.STRING, false))
        );
        EavSchemaVersionInfo second = version(
            secondDefinitionId,
            1,
            EavSchemaStatus.PUBLISHED,
            List.of(field(UUID.randomUUID(), "severity", EavDataType.STRING, false))
        );
        when(ticketTypes.findAll()).thenReturn(List.of(firstType, secondType));
        when(ticketTypes.findById(firstTypeId)).thenReturn(Optional.of(firstType));
        when(schemas.listVersions(firstDefinitionId)).thenReturn(List.of(first));
        when(schemas.listVersions(secondDefinitionId)).thenReturn(List.of(second));

        TicketQueryFieldCatalog catalog = new TicketQueryFieldCatalog(
            new EavQueryFieldCatalog(),
            schemas,
            instances,
            ticketTypes,
            tickets
        );

        Map<String, EavQueryField> global = catalog.fields(catalog.global());
        Map<String, EavQueryField> scoped = catalog.fields(catalog.forType(firstTypeId));

        assertThat(global).doesNotContainKey("severity");
        assertThat(scoped).containsKey("severity");
    }

    @Test
    @DisplayName("ticket scope resolves the exact historical schema version pinned by its EAV instance")
    void shouldResolveHistoricalTicketScope() {
        TicketTypeRepository ticketTypes = mock(TicketTypeRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        EavSchemaService schemas = mock(EavSchemaService.class);
        EavInstanceService instances = mock(EavInstanceService.class);
        UUID typeId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID historicalFieldId = UUID.randomUUID();
        EavSchemaVersionInfo historical = version(
            definitionId,
            1,
            EavSchemaStatus.RETIRED,
            List.of(field(historicalFieldId, "severity", EavDataType.STRING, false))
        );
        EavSchemaVersionInfo current = version(
            definitionId,
            2,
            EavSchemaStatus.PUBLISHED,
            List.of(field(UUID.randomUUID(), "impact", EavDataType.NUMBER, false))
        );
        Ticket ticket = Ticket.create(ticketId, typeId, instanceId, "Historical", "OPEN", UUID.randomUUID(), NOW);
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(instances.get(instanceId)).thenReturn(
            new EavInstanceView(instanceId, definitionId, historical.id(), Map.of("severity", "HIGH"))
        );
        when(schemas.version(definitionId, historical.id())).thenReturn(historical);
        when(schemas.listVersions(definitionId)).thenReturn(List.of(historical, current));

        TicketQueryFieldCatalog catalog = new TicketQueryFieldCatalog(
            new EavQueryFieldCatalog(),
            schemas,
            instances,
            ticketTypes,
            tickets
        );

        Map<String, EavQueryField> fields = catalog.fields(catalog.forTicket(ticketId));

        assertThat(fields).containsOnlyKeys("severity");
        assertThat(Objects.requireNonNull(fields.get("severity")).fieldId()).isEqualTo(historicalFieldId);
    }

    @Test
    @DisplayName(
        "missing ticket uses the safe global catalog rather than exposing existence through context resolution"
    )
    void shouldUseGlobalCatalogForMissingTicket() {
        TicketTypeRepository ticketTypes = mock(TicketTypeRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        EavSchemaService schemas = mock(EavSchemaService.class);
        EavInstanceService instances = mock(EavInstanceService.class);
        UUID ticketId = UUID.randomUUID();
        when(tickets.findById(ticketId)).thenReturn(Optional.empty());
        when(ticketTypes.findAll()).thenReturn(List.of());
        TicketQueryFieldCatalog catalog = new TicketQueryFieldCatalog(
            new EavQueryFieldCatalog(),
            schemas,
            instances,
            ticketTypes,
            tickets
        );

        QueryFieldContext context = catalog.forTicket(ticketId);

        assertThat(catalog.fields(context)).isEmpty();
    }

    private static EavFieldSpec field(UUID id, String key, EavDataType type, boolean required) {
        return new EavFieldSpec(id, key, key, type, required, Map.of());
    }

    private static EavSchemaVersionInfo version(
        UUID definitionId,
        long number,
        EavSchemaStatus status,
        List<EavFieldSpec> fields
    ) {
        Instant publishedAt = Instant.parse("2026-10-16T00:00:00Z");
        return new EavSchemaVersionInfo(
            UUID.randomUUID(),
            definitionId,
            number,
            status,
            fields,
            status == EavSchemaStatus.DRAFT ? Optional.empty() : Optional.of(publishedAt),
            status == EavSchemaStatus.RETIRED ? Optional.of(publishedAt.plusSeconds(60)) : Optional.empty()
        );
    }
}
