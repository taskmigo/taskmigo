package io.taskmigo.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.field.application.TicketFieldRegistry;
import io.taskmigo.ticket.field.application.api.TicketFieldStatus;
import io.taskmigo.ticket.field.application.api.TicketFieldType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TicketQueryFieldCatalogTest {

    @Test
    @DisplayName("archived fields remain in the stable registry snapshot")
    void shouldExposeArchivedFields() {
        TicketFieldRegistry registry = mock(TicketFieldRegistry.class);
        UUID fieldId = UUID.randomUUID();
        when(registry.findAll()).thenReturn(
            List.of(
                new TicketFieldRegistry.Entry(
                    fieldId,
                    "score",
                    "Score",
                    TicketFieldType.NUMBER,
                    TicketFieldStatus.ARCHIVED
                )
            )
        );
        TicketQueryFieldCatalog catalog = new TicketQueryFieldCatalog(registry);
        var global = catalog.fields(catalog.snapshot());

        assertThat(Objects.requireNonNull(global.get("score")).fieldId()).isEqualTo(fieldId);
        assertThat(Objects.requireNonNull(global.get("score")).dataType()).isEqualTo(EavDataType.NUMBER);
        assertThat(Objects.requireNonNull(global.get("score")).nullable()).isTrue();
    }

    @Test
    @DisplayName("query context captures registry metadata for one operation")
    void shouldCaptureStableSnapshot() {
        TicketFieldRegistry registry = mock(TicketFieldRegistry.class);
        var first = new TicketFieldRegistry.Entry(
            UUID.randomUUID(),
            "score",
            "Score",
            TicketFieldType.NUMBER,
            TicketFieldStatus.ACTIVE
        );
        var second = new TicketFieldRegistry.Entry(
            UUID.randomUUID(),
            "note",
            "Note",
            TicketFieldType.STRING,
            TicketFieldStatus.ACTIVE
        );
        when(registry.findAll()).thenReturn(List.of(first), List.of(first, second));
        TicketQueryFieldCatalog catalog = new TicketQueryFieldCatalog(registry);
        QueryFieldContext snapshot = catalog.snapshot();

        assertThat(catalog.fields(catalog.snapshot())).containsOnlyKeys("score", "note");
        assertThat(catalog.fields(snapshot)).containsOnlyKeys("score");
    }
}
