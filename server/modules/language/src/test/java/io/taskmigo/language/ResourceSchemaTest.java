package io.taskmigo.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResourceSchemaTest {

    private static final ResourceType USER = ResourceType.of("resource:user");
    private static final Field NAME = field("field:user:name", "name", LanguageType.Scalar.STRING, false);
    private static final Field SCORE = field("field:user:score", "score", LanguageType.Scalar.NUMBER, true);

    @Test
    @DisplayName("produces the same fingerprint when field declaration order changes")
    void shouldProduceSameFingerprintWhenFieldDeclarationOrderChanges() {
        ResourceSchema first = ResourceSchema.of(USER, List.of(NAME, SCORE));
        ResourceSchema second = ResourceSchema.of(USER, List.of(SCORE, NAME));

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    @Test
    @DisplayName("distinguishes schemas whose canonical field components contain delimiters")
    void shouldDistinguishFingerprintWhenFieldComponentsContainDelimiters() {
        ResourceSchema first = ResourceSchema.of(USER, List.of(field("a:b", "c", LanguageType.Scalar.STRING, false)));
        ResourceSchema second = ResourceSchema.of(USER, List.of(field("a", "b:c", LanguageType.Scalar.STRING, false)));

        assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
    }

    @Test
    @DisplayName("produces different fingerprints when semantic field contracts change")
    void shouldProduceDifferentFingerprintWhenSemanticFieldContractChanges() {
        ResourceSchema baseline = ResourceSchema.of(USER, List.of(NAME));

        assertThat(
            ResourceSchema.of(
                USER,
                List.of(field("field:user:display-name", "name", LanguageType.Scalar.STRING, false))
            ).fingerprint()
        ).isNotEqualTo(baseline.fingerprint());
        assertThat(
            ResourceSchema.of(
                USER,
                List.of(field("field:user:name", "displayName", LanguageType.Scalar.STRING, false))
            ).fingerprint()
        ).isNotEqualTo(baseline.fingerprint());
        assertThat(
            ResourceSchema.of(
                USER,
                List.of(field("field:user:name", "name", LanguageType.Scalar.NUMBER, false))
            ).fingerprint()
        ).isNotEqualTo(baseline.fingerprint());
        assertThat(
            ResourceSchema.of(
                USER,
                List.of(field("field:user:name", "name", LanguageType.Scalar.STRING, true))
            ).fingerprint()
        ).isNotEqualTo(baseline.fingerprint());
        assertThat(ResourceSchema.of(ResourceType.of("resource:group"), List.of(NAME)).fingerprint()).isNotEqualTo(
            baseline.fingerprint()
        );
    }

    @Test
    @DisplayName("keeps equal display paths distinct across resources")
    void shouldKeepEqualDisplayPathsDistinctWhenResourcesDiffer() {
        ResourceSchema user = ResourceSchema.of(USER, List.of(NAME));
        Field groupName = field("field:group:name", "name", LanguageType.Scalar.STRING, false);
        ResourceSchema group = ResourceSchema.of(ResourceType.of("resource:group"), List.of(groupName));

        assertThat(user.resolve(FieldPath.parse("name")).id()).isEqualTo(FieldId.of("field:user:name"));
        assertThat(group.resolve(FieldPath.parse("name")).id()).isEqualTo(FieldId.of("field:group:name"));
    }

    @Test
    @DisplayName("resolves runtime-created typed fields through the same schema contract")
    void shouldResolveTypedFieldWhenSchemaIsCreatedAtRuntime() {
        Field priority = field("field:runtime:priority", "priority", LanguageType.Scalar.NUMBER, true);
        ResourceSchema runtime = ResourceSchema.of(ResourceType.of("resource:runtime"), List.of(priority));

        assertThat(runtime.resolve(FieldPath.parse("priority"))).isEqualTo(priority);
    }

    @Test
    @DisplayName("rejects duplicate semantic field identities")
    void shouldRejectDuplicateFieldIdWhenSchemaIsCreated() {
        Field duplicate = field("field:user:name", "displayName", LanguageType.Scalar.STRING, false);

        assertThatThrownBy(() -> ResourceSchema.of(USER, List.of(NAME, duplicate)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("field id");
    }

    @Test
    @DisplayName("rejects duplicate field paths")
    void shouldRejectDuplicateFieldPathWhenSchemaIsCreated() {
        Field duplicate = field("field:user:other-name", "name", LanguageType.Scalar.STRING, false);

        assertThatThrownBy(() -> ResourceSchema.of(USER, List.of(NAME, duplicate)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("field path");
    }

    @Test
    @DisplayName("rejects unknown paths without falling back to a field name")
    void shouldRejectUnknownPathWhenFieldIsNotDeclared() {
        ResourceSchema schema = ResourceSchema.of(USER, List.of(NAME));

        assertThatThrownBy(() -> schema.resolve(FieldPath.parse("displayName")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("displayName");
    }

    @Test
    @DisplayName("rejects blank semantic identities and malformed paths")
    void shouldRejectInvalidValueWhenSemanticIdentityOrPathIsMalformed() {
        assertThatThrownBy(() -> ResourceType.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FieldId.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FieldPath.parse("name..value")).isInstanceOf(IllegalArgumentException.class);
    }

    private static Field field(String id, String path, LanguageType type, boolean nullable) {
        return new Field(FieldId.of(id), FieldPath.parse(path), type, nullable);
    }
}
