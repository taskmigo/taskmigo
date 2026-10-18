package io.taskmigo.ticket.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = TicketPersistenceIntegrationTest.TestApplication.class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class TicketPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine");

    private final JdbcTemplate jdbc;

    TicketPersistenceIntegrationTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("creates Ticket Type and Ticket tables from V1")
    void shouldCreateTicketTables() {
        Integer count = this.jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('ticket_types', 'tickets')",
            Integer.class
        );

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("enforces Ticket Type schema ownership and lifecycle constraints")
    void shouldEnforceTicketTypeIntegrity() {
        UUID missingDefinition = UUID.randomUUID();

        assertThatThrownBy(() ->
            this.jdbc.update(
                "INSERT INTO ticket_types (id, type_key, name, status, schema_definition_id, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(),
                "missing-schema",
                "Missing schema",
                missingDefinition
            )
        ).isInstanceOf(DataIntegrityViolationException.class);

        UUID definition = insertDefinition("type-status");
        assertThatThrownBy(() ->
            this.jdbc.update(
                "INSERT INTO ticket_types (id, type_key, name, status, schema_definition_id, created_at, updated_at) VALUES (?, ?, ?, 'UNKNOWN', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(),
                "invalid-status",
                "Invalid status",
                definition
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("enforces Ticket foreign keys and one Ticket per EAV schema instance")
    void shouldEnforceTicketIdentityGraph() {
        UUID creator = insertUser("ticket-creator");
        UUID definition = insertDefinition("ticket-graph");
        UUID schemaVersion = insertVersion(definition, 1);
        UUID instance = insertInstance(definition, schemaVersion);
        UUID ticketType = insertTicketType(definition, "graph");

        insertTicket(UUID.randomUUID(), ticketType, instance, creator, "First");

        assertThatThrownBy(() ->
            insertTicket(UUID.randomUUID(), ticketType, instance, creator, "Duplicate instance")
        ).isInstanceOf(DataIntegrityViolationException.class);

        UUID otherInstance = insertInstance(definition, schemaVersion);
        assertThatThrownBy(() ->
            insertTicket(UUID.randomUUID(), UUID.randomUUID(), otherInstance, creator, "Missing type")
        ).isInstanceOf(DataIntegrityViolationException.class);

        UUID thirdInstance = insertInstance(definition, schemaVersion);
        assertThatThrownBy(() ->
            insertTicket(UUID.randomUUID(), ticketType, thirdInstance, UUID.randomUUID(), "Missing creator")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertUser(String username) {
        UUID id = UUID.randomUUID();
        this.jdbc.update(
            "INSERT INTO users (id, username, first_name, last_name, status) VALUES (?, ?, 'Ticket', 'Creator', 'ACTIVE')",
            id,
            username
        );
        return id;
    }

    private UUID insertDefinition(String key) {
        UUID id = UUID.randomUUID();
        this.jdbc.update("INSERT INTO eav_schema_definitions (id, schema_key, name) VALUES (?, ?, ?)", id, key, key);
        return id;
    }

    private UUID insertVersion(UUID definitionId, long version) {
        UUID id = UUID.randomUUID();
        this.jdbc.update(
            "INSERT INTO eav_schema_versions (id, schema_definition_id, version, status, published_at) VALUES (?, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP)",
            id,
            definitionId,
            version
        );
        this.jdbc.update("UPDATE eav_schema_definitions SET current_version_id = ? WHERE id = ?", id, definitionId);
        return id;
    }

    private UUID insertInstance(UUID definitionId, UUID versionId) {
        UUID id = UUID.randomUUID();
        this.jdbc.update(
            "INSERT INTO eav_schema_instances (id, schema_definition_id, schema_version_id) VALUES (?, ?, ?)",
            id,
            definitionId,
            versionId
        );
        return id;
    }

    private UUID insertTicketType(UUID definitionId, String key) {
        UUID id = UUID.randomUUID();
        this.jdbc.update(
            "INSERT INTO ticket_types (id, type_key, name, status, schema_definition_id, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            id,
            key,
            key,
            definitionId
        );
        return id;
    }

    private void insertTicket(UUID id, UUID typeId, UUID instanceId, UUID creatorId, String title) {
        this.jdbc.update(
            "INSERT INTO tickets (id, ticket_type_id, schema_instance_id, title, status, created_by, created_at, updated_at, version) VALUES (?, ?, ?, ?, 'OPEN', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
            id,
            typeId,
            instanceId,
            title,
            creatorId
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}
}
