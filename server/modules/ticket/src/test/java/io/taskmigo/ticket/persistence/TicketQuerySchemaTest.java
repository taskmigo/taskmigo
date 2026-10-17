package io.taskmigo.ticket.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.eav.query.EavQueryField;
import io.taskmigo.eav.query.EavQueryFieldCatalog;
import io.taskmigo.eav.query.jpa.EavJpaQueryFieldProvider;
import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.eav.value.application.EavInstanceService;
import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.TypeDescriptor;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import io.taskmigo.ticket.application.TicketRepository;
import io.taskmigo.ticket.type.application.TicketTypeRepository;
import io.taskmigo.ticket.type.domain.TicketType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TicketQuerySchemaTest {

    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

    @Test
    @DisplayName("Ticket QuerySchemas expose flat custom fields and global fallback fails closed for ambiguous keys")
    void shouldExposeFlatScopedFieldsAndGlobalFallback() {
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
        EavSchemaVersionInfo firstVersion = version(
            firstDefinitionId,
            List.of(field(UUID.randomUUID(), "severity", EavDataType.STRING))
        );
        EavSchemaVersionInfo secondVersion = version(
            secondDefinitionId,
            List.of(field(UUID.randomUUID(), "severity", EavDataType.ENUM))
        );
        when(ticketTypes.findAll()).thenReturn(List.of(firstType, secondType));
        when(ticketTypes.findById(firstTypeId)).thenReturn(Optional.of(firstType));
        when(schemas.listVersions(firstDefinitionId)).thenReturn(List.of(firstVersion));
        when(schemas.listVersions(secondDefinitionId)).thenReturn(List.of(secondVersion));
        TicketQueryFieldCatalog catalog = new TicketQueryFieldCatalog(
            new EavQueryFieldCatalog(),
            schemas,
            instances,
            ticketTypes,
            tickets
        );
        EavJpaQueryFieldProvider queryFields = queryFields();
        ListTicketsQuerySchema schema = new ListTicketsQuerySchema(catalog, queryFields);

        QueryFieldContext global = QueryFieldContext.empty();
        QueryFieldContext scoped = catalog.forType(firstTypeId);
        List<QueryFieldDescriptor> globalFields = List.copyOf(schema.fields(global));
        List<QueryFieldDescriptor> scopedFields = List.copyOf(schema.fields(scoped));

        assertThat(globalFields)
            .extracting(field -> field.path().text())
            .doesNotContain("severity", "fields.severity");
        assertThat(scopedFields)
            .extracting(field -> field.path().text())
            .contains("severity")
            .doesNotContain("fields.severity");
        assertThat(schema.identity(global)).isNotEqualTo(schema.identity(scoped));
        assertThat(schema.operation()).isEqualTo("tickets.list");
        assertThat(new ReadTicketQuerySchema(catalog, queryFields).operation()).isEqualTo("tickets.read");
        assertThat(new UpdateTicketQuerySchema(catalog, queryFields).operation()).isEqualTo("tickets.update");
    }

    @Test
    @DisplayName("only approved common Ticket fields are sortable and id remains tie-break only")
    void shouldExposeApprovedStaticSortability() {
        TicketQueryFieldCatalog catalog = mock(TicketQueryFieldCatalog.class);
        when(catalog.fields(QueryFieldContext.empty())).thenReturn(Map.of());
        ListTicketsQuerySchema schema = new ListTicketsQuerySchema(catalog, mock(EavJpaQueryFieldProvider.class));

        Map<String, QueryFieldDescriptor> descriptors = schema
            .fields(QueryFieldContext.empty())
            .stream()
            .collect(Collectors.toMap(field -> field.path().text(), field -> field));

        assertThat(Objects.requireNonNull(descriptors.get("title")).sortable()).isTrue();
        assertThat(Objects.requireNonNull(descriptors.get("status")).sortable()).isTrue();
        assertThat(Objects.requireNonNull(descriptors.get("createdAt")).sortable()).isTrue();
        assertThat(Objects.requireNonNull(descriptors.get("updatedAt")).sortable()).isTrue();
        assertThat(Objects.requireNonNull(descriptors.get("id")).sortable()).isFalse();
        assertThat(descriptors).containsKeys("ticketTypeId", "createdBy");
        assertThat(descriptors).doesNotContainKey("schemaInstanceId");
    }

    private static EavJpaQueryFieldProvider queryFields() {
        EavJpaQueryFieldProvider provider = mock(EavJpaQueryFieldProvider.class);
        when(provider.create(any(), any())).thenAnswer(invocation -> {
            EavQueryField field = invocation.getArgument(0);
            QueryField<TicketEntity, String> queryField = mock();
            when(queryField.descriptor()).thenReturn(
                new QueryFieldDescriptor(
                    QueryPath.of(field.key()),
                    TypeDescriptor.of(String.class),
                    field.nullable(),
                    field.operators(),
                    field.sortable(),
                    field.fieldId() + ":" + field.dataType().name()
                )
            );
            return queryField;
        });
        return provider;
    }

    private static EavFieldSpec field(UUID id, String key, EavDataType type) {
        Map<String, Object> constraints =
            type == EavDataType.ENUM ? Map.of("options", List.of("HIGH", "LOW")) : Map.of();
        return new EavFieldSpec(id, key, key, type, false, constraints);
    }

    private static EavSchemaVersionInfo version(UUID definitionId, List<EavFieldSpec> fields) {
        return new EavSchemaVersionInfo(
            UUID.randomUUID(),
            definitionId,
            1,
            EavSchemaStatus.PUBLISHED,
            fields,
            Optional.of(NOW),
            Optional.empty()
        );
    }
}
