package io.taskmigo.eav.schema.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.eav.EavTestApplication;
import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = EavTestApplication.class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class JpaEavSchemaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine");

    private final EavSchemaService schemas;

    JpaEavSchemaRepositoryIntegrationTest(EavSchemaService schemas) {
        this.schemas = schemas;
    }

    @Test
    @DisplayName("persists schema lifecycle and clones current fields through the JPA adapter")
    void shouldPersistSchemaLifecycleThroughJpaAdapter() {
        UUID definitionId = this.schemas.createDefinition("bug-jpa", "Bug");
        UUID fieldId = UUID.randomUUID();
        EavSchemaVersionInfo v1 = this.schemas.createDraft(definitionId);
        v1 = this.schemas.replaceDraftFields(
            definitionId,
            v1.id(),
            List.of(new EavFieldSpec(fieldId, "severity", "Severity", EavDataType.STRING, true, Map.of()))
        );
        EavSchemaVersionInfo published = this.schemas.publish(definitionId, v1.id());

        assertThat(this.schemas.current(definitionId)).contains(published);

        EavSchemaVersionInfo v2 = this.schemas.createDraft(definitionId);
        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.fields()).singleElement().extracting(EavFieldSpec::fieldId).isEqualTo(fieldId);

        EavSchemaVersionInfo retired = this.schemas.retire(definitionId, v1.id());
        assertThat(retired.status()).isEqualTo(EavSchemaStatus.RETIRED);
        assertThat(this.schemas.version(definitionId, v1.id())).isEqualTo(retired);
    }
}
