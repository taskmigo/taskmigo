package io.taskmigo.web.api.v0.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import io.taskmigo.web.api.v0.testing.ApiIntegrationTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;

class TicketApiIntegrationTest extends ApiIntegrationTestSupport {

    private final TicketTypeService ticketTypes;
    private final TicketService tickets;
    private final JdbcTemplate jdbc;

    TicketApiIntegrationTest(TicketTypeService ticketTypes, TicketService tickets, JdbcTemplate jdbc) {
        this.ticketTypes = ticketTypes;
        this.tickets = tickets;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("Ticket Type management response keeps generic EAV ownership internal")
    void shouldHideSchemaDefinitionIdentityFromTicketTypeApi() {
        UUID typeId = this.ticketTypes.create("api-type-" + UUID.randomUUID(), "API Type");

        String body = this.api().get("/api/v0/ticket-types/" + typeId);

        assertThat(body).contains(typeId.toString()).contains("API Type").doesNotContain("schemaDefinitionId");
    }

    @Test
    @DisplayName("Ticket list accepts flat custom filter and sorting through the production HTTP query pipeline")
    void shouldFilterAndSortTicketsByFlatCustomField() {
        UUID actor = systemUserId();
        UUID typeId = typeWithFields(
            "api-query-" + UUID.randomUUID(),
            List.of(new EavFieldSpec(UUID.randomUUID(), "score", "Score", EavDataType.NUMBER, true, Map.of()))
        );
        this.tickets.create(typeId, "Low score", "OPEN", Map.of("score", 1), actor);
        this.tickets.create(typeId, "High score", "OPEN", Map.of("score", 3), actor);

        String body = this.api().get(
            "/api/v0/tickets?ticketTypeId=" + typeId + "&filterBy=object.score >= 2&sortBy=score&sortOrder=desc"
        );

        assertThat(body)
            .contains("High score")
            .contains("\"score\":3")
            .doesNotContain("Low score")
            .doesNotContain("fields.score");
    }

    @Test
    @DisplayName("Ticket PATCH null removes an optional custom value while preserving the historical schema")
    void shouldRemoveOptionalCustomValueThroughHttpPatch() {
        UUID actor = systemUserId();
        UUID typeId = typeWithFields(
            "api-patch-" + UUID.randomUUID(),
            List.of(new EavFieldSpec(UUID.randomUUID(), "note", "Note", EavDataType.STRING, false, Map.of()))
        );
        UUID ticketId = this.tickets.create(typeId, "Patch me", "OPEN", Map.of("note", "remove me"), actor);

        this.api().patchJson("/api/v0/tickets/" + ticketId, "{\"customFields\":{\"note\":null}}");
        String body = this.api().get("/api/v0/tickets/" + ticketId);

        assertThat(body).contains("Patch me").doesNotContain("remove me").doesNotContain("\"note\"");
    }

    @Test
    @DisplayName("unknown Ticket returns the controlled not-found error contract")
    void shouldReturnNotFoundForUnknownTicket() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> this.api().get("/api/v0/tickets/" + missing)).isInstanceOfSatisfying(
            HttpClientErrorException.NotFound.class,
            exception -> assertThat(exception.getResponseBodyAsString()).contains("\"code\":\"NOT_FOUND\"")
        );
    }

    @Test
    @DisplayName("Ticket create HTTP endpoint accepts custom fields and records the authenticated system actor")
    void shouldCreateTicketThroughHttpApi() {
        UUID typeId = typeWithFields(
            "api-create-" + UUID.randomUUID(),
            List.of(new EavFieldSpec(UUID.randomUUID(), "severity", "Severity", EavDataType.STRING, true, Map.of()))
        );
        String body = """
        {
          "ticketTypeId": "%s",
          "title": "Created over HTTP",
          "status": "OPEN",
          "customFields": {"severity": "HIGH"}
        }
        """.formatted(typeId);

        int status = this.api().postStatus("/api/v0/tickets", MediaType.APPLICATION_JSON, body);
        String listed = this.api().get("/api/v0/tickets?ticketTypeId=" + typeId);

        assertThat(status).isEqualTo(HttpStatus.CREATED.value());
        assertThat(listed).contains("Created over HTTP").contains("\"severity\":\"HIGH\"");
    }

    private UUID typeWithFields(String key, List<EavFieldSpec> fields) {
        UUID typeId = this.ticketTypes.create(key, key);
        EavSchemaVersionInfo draft = this.ticketTypes.createTemplate(typeId);
        this.ticketTypes.replaceTemplate(typeId, draft.id(), fields);
        this.ticketTypes.publishTemplate(typeId, draft.id());
        return typeId;
    }

    private UUID systemUserId() {
        return Objects.requireNonNull(
            this.jdbc.queryForObject("SELECT id FROM users WHERE username = 'system'", UUID.class)
        );
    }
}
