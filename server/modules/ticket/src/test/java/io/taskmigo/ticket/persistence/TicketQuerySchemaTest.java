package io.taskmigo.ticket.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.eav.query.EavQueryField;
import io.taskmigo.eav.query.jpa.EavJpaQueryFieldProvider;
import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.TypeDescriptor;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import io.taskmigo.ticket.field.application.TicketFieldRegistry;
import io.taskmigo.ticket.field.application.api.TicketFieldStatus;
import io.taskmigo.ticket.field.application.api.TicketFieldType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TicketQuerySchemaTest {

    @Test
    @DisplayName("Ticket query schemas expose registered fields for all operations")
    void shouldExposeStableRegisteredFields() {
        TicketFieldRegistry registry = mock(TicketFieldRegistry.class);
        when(registry.findAll()).thenReturn(
            List.of(
                new TicketFieldRegistry.Entry(
                    UUID.randomUUID(),
                    "severity",
                    "Severity",
                    TicketFieldType.STRING,
                    TicketFieldStatus.ACTIVE
                )
            )
        );
        TicketQueryFieldCatalog catalog = new TicketQueryFieldCatalog(registry);
        EavJpaQueryFieldProvider queryFields = queryFields();
        ListTicketsQuerySchema schema = new ListTicketsQuerySchema(catalog, queryFields);

        QueryFieldContext snapshot = catalog.snapshot();
        assertThat(schema.fields(snapshot))
            .extracting(field -> field.path().text())
            .contains("severity");
        assertThat(new ReadTicketQuerySchema(catalog, queryFields).fields(snapshot))
            .extracting(field -> field.path().text())
            .contains("severity");
        assertThat(new UpdateTicketQuerySchema(catalog, queryFields).fields(snapshot))
            .extracting(field -> field.path().text())
            .contains("severity");
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
}
