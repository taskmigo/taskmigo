package io.taskmigo.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.AuthorizationResource.Statement;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

class AuthorizationResourceSerializationTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final YAMLMapper yaml = YAMLMapper.builder().build();

    @Test
    void yamlAndJsonUseTheSameCanonicalStatementModel() throws Exception {
        Statement fromYaml = this.yaml.readValue(
            """
            key: project.read
            match:
              method: GET
              path: /api/v0/projects
            target: object
            effect: allow
            when: object.organizationId == principal.organizationId
            """,
            Statement.class
        );
        Statement fromJson = this.json.readValue(
            """
            {
              "key": "project.read",
              "match": {"method": "GET", "path": "/api/v0/projects"},
              "target": "object",
              "effect": "allow",
              "when": "object.organizationId == principal.organizationId"
            }
            """,
            Statement.class
        );

        assertThat(fromJson).isEqualTo(fromYaml);
        assertThat(fromJson.fields()).isEmpty();
    }

    @Test
    void customerPayloadCannotDeclareTrustedOrigin() throws Exception {
        Statement supplied = this.json.readValue(
            """
            {
              "key": "project.read",
              "match": {"method": "GET", "path": "/api/v0/projects"},
              "target": "request",
              "effect": "allow",
              "origin": "system"
            }
            """,
            Statement.class
        );

        assertThat(supplied).isEqualTo(
            this.json.readValue(
                """
                {
                  "key": "project.read",
                  "match": {"method": "GET", "path": "/api/v0/projects"},
                  "target": "request",
                  "effect": "allow"
                }
                """,
                Statement.class
            )
        );
        assertThat(Statement.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .doesNotContain("origin");
    }
}
