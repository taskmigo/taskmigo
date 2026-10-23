package io.taskmigo.eav.value.persistence;

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

@SpringBootTest(classes = EavValuePersistenceIntegrationTest.TestApplication.class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EavValuePersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine");

    private final JdbcTemplate jdbc;

    EavValuePersistenceIntegrationTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("creates generic EAV instance and typed value tables from V1")
    void shouldCreateEavValueTables() {
        Integer count = this.jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('eav_schema_instances', 'eav_field_values')",
            Integer.class
        );

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("rejects values whose logical field belongs to another schema version")
    void shouldRejectCrossVersionField() {
        UUID definition = UUID.randomUUID();
        UUID firstVersion = UUID.randomUUID();
        UUID secondVersion = UUID.randomUUID();
        UUID firstField = UUID.randomUUID();
        UUID secondField = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        this.insertDefinition(definition, "cross-version");
        this.insertVersion(firstVersion, definition, 1);
        this.insertVersion(secondVersion, definition, 2);
        this.insertField(firstVersion, firstField, "STRING");
        this.insertField(secondVersion, secondField, "STRING");
        this.insertInstance(instance, definition, firstVersion);

        assertThatThrownBy(() ->
            this.jdbc.update(
                "INSERT INTO eav_field_values (schema_instance_id, schema_version_id, field_id, data_type, string_value) VALUES (?, ?, ?, 'STRING', ?)",
                instance,
                firstVersion,
                secondField,
                "invalid"
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects values whose data type disagrees with the historical field definition")
    void shouldRejectWrongDataType() {
        UUID definition = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID field = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        this.insertDefinition(definition, "wrong-type");
        this.insertVersion(version, definition, 1);
        this.insertField(version, field, "STRING");
        this.insertInstance(instance, definition, version);

        assertThatThrownBy(() ->
            this.jdbc.update(
                "INSERT INTO eav_field_values (schema_instance_id, schema_version_id, field_id, data_type, number_value) VALUES (?, ?, ?, 'NUMBER', 1)",
                instance,
                version,
                field
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects explicit null value rows")
    void shouldRejectAllNullValueRow() {
        UUID definition = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID field = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        this.insertDefinition(definition, "all-null");
        this.insertVersion(version, definition, 1);
        this.insertField(version, field, "STRING");
        this.insertInstance(instance, definition, version);

        assertThatThrownBy(() ->
            this.jdbc.update(
                "INSERT INTO eav_field_values (schema_instance_id, schema_version_id, field_id, data_type) VALUES (?, ?, ?, 'STRING')",
                instance,
                version,
                field
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("keeps historical typed values after their schema version is retired")
    void shouldKeepValuesAfterSchemaRetirement() {
        UUID definition = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID field = UUID.randomUUID();
        UUID instance = UUID.randomUUID();
        this.insertDefinition(definition, "history-value");
        this.insertVersion(version, definition, 1);
        this.insertField(version, field, "STRING");
        this.insertInstance(instance, definition, version);
        this.jdbc.update(
            "INSERT INTO eav_field_values (schema_instance_id, schema_version_id, field_id, data_type, string_value) VALUES (?, ?, ?, 'STRING', ?)",
            instance,
            version,
            field,
            "legacy"
        );

        this.jdbc.update(
            "UPDATE eav_schema_versions SET status = 'RETIRED', retired_at = CURRENT_TIMESTAMP WHERE id = ?",
            version
        );

        String value = this.jdbc.queryForObject(
            "SELECT string_value FROM eav_field_values WHERE schema_instance_id = ? AND field_id = ?",
            String.class,
            instance,
            field
        );
        assertThat(value).isEqualTo("legacy");
    }

    private void insertDefinition(UUID id, String key) {
        this.jdbc.update("INSERT INTO eav_schema_definitions (id, schema_key, name) VALUES (?, ?, ?)", id, key, key);
    }

    private void insertVersion(UUID id, UUID definitionId, long version) {
        this.jdbc.update(
            "INSERT INTO eav_schema_versions (id, schema_definition_id, version, status, published_at) VALUES (?, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP)",
            id,
            definitionId,
            version
        );
    }

    private void insertField(UUID versionId, UUID fieldId, String dataType) {
        this.jdbc.update(
            "INSERT INTO eav_field_definitions (id, schema_version_id, field_id, field_key, label, data_type, required) VALUES (?, ?, ?, ?, ?, ?, false)",
            UUID.randomUUID(),
            versionId,
            fieldId,
            "field-" + fieldId,
            "Field",
            dataType
        );
    }

    private void insertInstance(UUID id, UUID definitionId, UUID versionId) {
        this.jdbc.update(
            "INSERT INTO eav_schema_instances (id, schema_definition_id, schema_version_id) VALUES (?, ?, ?)",
            id,
            definitionId,
            versionId
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}
}
