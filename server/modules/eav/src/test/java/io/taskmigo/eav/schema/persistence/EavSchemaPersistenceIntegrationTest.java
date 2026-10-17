package io.taskmigo.eav.schema.persistence;

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

@SpringBootTest(classes = EavSchemaPersistenceIntegrationTest.TestApplication.class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EavSchemaPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine");

    private final JdbcTemplate jdbc;

    EavSchemaPersistenceIntegrationTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("creates generic schema definition version and field definition tables from V1")
    void shouldCreateGenericEavSchemaTables() {
        Integer count = this.jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('eav_schema_definitions', 'eav_schema_versions', 'eav_field_definitions')",
            Integer.class
        );

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("rejects selecting a current version owned by another schema definition")
    void shouldRejectCrossDefinitionCurrentVersion() {
        UUID firstDefinition = UUID.randomUUID();
        UUID secondDefinition = UUID.randomUUID();
        UUID secondVersion = UUID.randomUUID();
        this.insertDefinition(firstDefinition, "first");
        this.insertDefinition(secondDefinition, "second");
        this.insertVersion(secondVersion, secondDefinition, 1, "PUBLISHED", true, false);

        assertThatThrownBy(() ->
            this.jdbc.update(
                "UPDATE eav_schema_definitions SET current_version_id = ? WHERE id = ?",
                secondVersion,
                firstDefinition
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects duplicate version numbers inside one schema definition")
    void shouldRejectDuplicateVersionNumber() {
        UUID definition = UUID.randomUUID();
        this.insertDefinition(definition, "duplicate-version");
        this.insertVersion(UUID.randomUUID(), definition, 1, "DRAFT", false, false);

        assertThatThrownBy(() ->
            this.insertVersion(UUID.randomUUID(), definition, 1, "DRAFT", false, false)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects lifecycle states whose timestamps do not match the schema status")
    void shouldRejectInvalidLifecycleTimestamps() {
        UUID definition = UUID.randomUUID();
        this.insertDefinition(definition, "invalid-lifecycle");

        assertThatThrownBy(() ->
            this.insertVersion(UUID.randomUUID(), definition, 1, "PUBLISHED", false, false)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("retiring a published version keeps the historical schema row resolvable")
    void shouldKeepRetiredVersionResolvable() {
        UUID definition = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        this.insertDefinition(definition, "history");
        this.insertVersion(version, definition, 1, "PUBLISHED", true, false);

        int updated = this.jdbc.update(
            "UPDATE eav_schema_versions SET status = 'RETIRED', retired_at = CURRENT_TIMESTAMP WHERE id = ?",
            version
        );
        Integer count = this.jdbc.queryForObject(
            "SELECT count(*) FROM eav_schema_versions WHERE id = ? AND schema_definition_id = ? AND status = 'RETIRED'",
            Integer.class,
            version,
            definition
        );

        assertThat(updated).isEqualTo(1);
        assertThat(count).isEqualTo(1);
    }

    private void insertDefinition(UUID id, String key) {
        this.jdbc.update("INSERT INTO eav_schema_definitions (id, schema_key, name) VALUES (?, ?, ?)", id, key, key);
    }

    private void insertVersion(
        UUID id,
        UUID definitionId,
        long version,
        String status,
        boolean published,
        boolean retired
    ) {
        this.jdbc.update(
            "INSERT INTO eav_schema_versions (id, schema_definition_id, version, status, published_at, retired_at) VALUES (?, ?, ?, ?, CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END, CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)",
            id,
            definitionId,
            version,
            status,
            published,
            retired
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}
}
