package io.taskmigo.web.api.v0.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.ticket.field.application.api.TicketFieldService;
import io.taskmigo.ticket.field.application.api.TicketFieldType;
import io.taskmigo.ticket.type.application.api.TicketFieldSpec;
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
    private final TicketFieldService fieldService;
    private final TicketService tickets;
    private final JdbcTemplate jdbc;

    TicketApiIntegrationTest(
        TicketTypeService ticketTypes,
        TicketFieldService fieldService,
        TicketService tickets,
        JdbcTemplate jdbc
    ) {
        this.ticketTypes = ticketTypes;
        this.fieldService = fieldService;
        this.tickets = tickets;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("Ticket Type management response keeps generic EAV ownership internal")
    void shouldHideSchemaDefinitionIdentityFromTicketTypeApi() {
        UUID typeId = this.ticketTypes.create("api-type-" + UUID.randomUUID(), "API Type", List.of()).id();

        String body = this.api().get("/api/v0/ticket-types/" + typeId);

        assertThat(body)
            .contains(typeId.toString())
            .contains("API Type")
            .contains("currentTemplate")
            .doesNotContain("schemaDefinitionId");
    }

    @Test
    @DisplayName("Ticket list accepts flat custom filter and sorting through the production HTTP query pipeline")
    void shouldFilterAndSortTicketsByFlatCustomField() {
        UUID actor = systemUserId();
        UUID typeId = typeWithFields(
            "api-query-" + UUID.randomUUID(),
            List.of(field("score", "Score", TicketFieldType.NUMBER, true))
        );
        this.tickets.create(typeId, "Low score", "OPEN", Map.of("score", 1), actor, null);
        this.tickets.create(typeId, "High score", "OPEN", Map.of("score", 3), actor, null);

        String body = this.api().get(
            "/api/v0/tickets?ticketTypeId=" + typeId + "&filterBy=object.score >= 2&sortBy=-score"
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
            List.of(field("note", "Note", TicketFieldType.STRING, false))
        );
        UUID ticketId = this.tickets.create(typeId, "Patch me", "OPEN", Map.of("note", "remove me"), actor, null);

        this.api().patchJson("/api/v0/tickets/" + ticketId, "{\"customFields\":{\"note\":null}}");
        String body = this.api().get("/api/v0/tickets/" + ticketId);

        assertThat(body).contains("Patch me").doesNotContain("remove me").contains("template").contains("\"note\"");
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
            List.of(field("severity", "Severity", TicketFieldType.STRING, true))
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

    @Test
    @DisplayName("registry management API creates fields independently and exposes query metadata")
    void shouldRegisterFieldOverHttp() {
        String key = "estimate" + UUID.randomUUID().toString().replace("-", "");
        String body = """
        {"key":"%s","label":"Estimate","dataType":"NUMBER"}
        """.formatted(key);
        int status = this.api().postStatus("/api/v0/ticket-fields", MediaType.APPLICATION_JSON, body);
        assertThat(status).isEqualTo(HttpStatus.CREATED.value());
        UUID fieldId = this.fieldService
            .list()
            .stream()
            .filter(field -> field.key().equals(key))
            .findFirst()
            .orElseThrow()
            .id();
        String registered = this.api().get("/api/v0/ticket-fields/" + fieldId);
        assertThat(registered).contains(key).contains("NUMBER").contains("sortable");
    }

    @Test
    @DisplayName("field management reports domain failures and retains archived definitions")
    void shouldExposeFieldLifecycleAndErrorContract() {
        String key = "lifecycle" + UUID.randomUUID().toString().replace("-", "");
        String body = """
        {"key":"%s","label":"Field","dataType":"NUMBER"}
        """.formatted(key);
        assertThat(this.api().postStatus("/api/v0/ticket-fields", MediaType.APPLICATION_JSON, body)).isEqualTo(
            HttpStatus.CREATED.value()
        );
        assertThat(this.api().postStatus("/api/v0/ticket-fields", MediaType.APPLICATION_JSON, body)).isEqualTo(
            HttpStatus.CONFLICT.value()
        );

        UUID id = this.fieldService
            .list()
            .stream()
            .filter(field -> field.key().equals(key))
            .findFirst()
            .orElseThrow()
            .id();
        String path = "/api/v0/ticket-fields/" + id;
        this.api().patchJson(path, "{\"status\":\"ARCHIVED\"}");
        assertThat(this.api().get(path)).contains("ARCHIVED").contains(key);
        assertThatThrownBy(() -> this.api().patchJson(path, "{\"status\":\"ACTIVE\"}")).isInstanceOf(
            HttpClientErrorException.Conflict.class
        );
        assertThatThrownBy(() -> this.api().get("/api/v0/ticket-fields/" + UUID.randomUUID())).isInstanceOf(
            HttpClientErrorException.NotFound.class
        );
    }

    @Test
    @DisplayName(
        "creates Type with an active template in one request and returns historical schema with Ticket details"
    )
    void shouldCreateTypeAndDiscoverTemplateInOneRequest() {
        UUID fieldId = this.fieldService.create("apiAtomic", "Atomic", TicketFieldType.STRING).id();
        String key = "atomic-" + UUID.randomUUID();
        String body = """
        {"key":"%s","name":"Atomic","fields":[{"fieldId":"%s","required":true}]}
        """.formatted(key, fieldId);
        assertThat(this.api().postStatus("/api/v0/ticket-types", MediaType.APPLICATION_JSON, body)).isEqualTo(
            HttpStatus.CREATED.value()
        );
        var type = this.ticketTypes
            .list()
            .stream()
            .filter(value -> value.key().equals(key))
            .findFirst()
            .orElseThrow();
        UUID previous = type.currentTemplate().id();
        UUID ticketId = this.tickets.create(
            type.id(),
            "Historical",
            "OPEN",
            Map.of("apiAtomic", "value"),
            systemUserId(),
            previous
        );
        String path = "/api/v0/ticket-types/" + type.id() + "/templates";
        String revision = """
        {"expectedTemplateVersionId":"%s","fields":[]}
        """.formatted(previous);
        assertThat(this.api().postStatus(path, MediaType.APPLICATION_JSON, revision)).isEqualTo(
            HttpStatus.CREATED.value()
        );
        assertThat(this.api().postStatus(path, MediaType.APPLICATION_JSON, revision)).isEqualTo(
            HttpStatus.CONFLICT.value()
        );
        assertThat(this.api().get("/api/v0/tickets/" + ticketId))
            .contains(previous.toString())
            .contains("apiAtomic")
            .contains("SUPERSEDED");
        assertThat(this.api().get(path)).contains("ACTIVE").contains("SUPERSEDED").doesNotContain("DRAFT");
    }

    private TicketFieldSpec field(String key, String label, TicketFieldType type, boolean required) {
        return new TicketFieldSpec(this.fieldService.create(key, label, type).id(), null, required, Map.of());
    }

    private UUID typeWithFields(String key, List<TicketFieldSpec> fields) {
        return this.ticketTypes.create(key, key, fields).id();
    }

    private UUID systemUserId() {
        return Objects.requireNonNull(
            this.jdbc.queryForObject("SELECT id FROM users WHERE username = 'system'", UUID.class)
        );
    }
}
