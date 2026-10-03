package io.taskmigo.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResolvedResourceReferenceTest {

    @Test
    @DisplayName("stores resolved resource and field identities in compiled references")
    void shouldStoreResolvedIdentitiesWhenResourceFieldIsCompiled() {
        ResourceSchema schema = schema(
            "resource:ticket",
            "field:ticket:priority",
            "priority",
            LanguageType.Scalar.NUMBER
        );
        CompilerEnvironment environment = CompilerEnvironment.of(
            Map.of("object", new CompilerEnvironment.Root(schema, true))
        );

        SemanticAst program = new EmbeddedLanguageCompiler().compile("return object.priority >= 3;", environment);
        SemanticAst.Binary comparison = (SemanticAst.Binary) program.expression();
        SemanticAst.Reference reference = (SemanticAst.Reference) comparison.left();

        assertThat(reference.resourceType()).isEqualTo(ResourceType.of("resource:ticket"));
        assertThat(reference.fieldId()).isEqualTo(FieldId.of("field:ticket:priority"));
        assertThat(reference.fieldPath()).isEqualTo(FieldPath.parse("priority"));
        assertThat(reference.type()).isEqualTo(LanguageType.Scalar.NUMBER);
        assertThat(reference.symbolic()).isTrue();
        assertThat(program.schemaFingerprints())
            .containsExactlyEntriesOf(Map.of(ResourceType.of("resource:ticket"), schema.fingerprint()));
    }

    @Test
    @DisplayName("resolves equal field paths to the identity of their bound resource")
    void shouldResolveDifferentFieldIdentityWhenDisplayPathsAreEqual() {
        ResourceSchema user = schema("resource:user", "field:user:name", "name", LanguageType.Scalar.STRING);
        ResourceSchema group = schema("resource:group", "field:group:name", "name", LanguageType.Scalar.STRING);

        SemanticAst userProgram = compile("actor", user, "return actor.name;");
        SemanticAst groupProgram = compile("actor", group, "return actor.name;");

        assertThat(((SemanticAst.Reference) userProgram.expression()).fieldId())
            .isEqualTo(FieldId.of("field:user:name"));
        assertThat(((SemanticAst.Reference) groupProgram.expression()).fieldId())
            .isEqualTo(FieldId.of("field:group:name"));
    }

    @Test
    @DisplayName("preserves resolved identity when a resource root is accessed through a local alias")
    void shouldPreserveResolvedIdentityWhenRootIsAliased() {
        ResourceSchema schema = schema("resource:user", "field:user:name", "name", LanguageType.Scalar.STRING);
        CompilerEnvironment environment = CompilerEnvironment.of(
            Map.of("object", new CompilerEnvironment.Root(schema, false))
        );

        SemanticAst program = new EmbeddedLanguageCompiler().compile(
            "const alias = object; return alias.name;",
            environment
        );
        SemanticAst.Reference reference = (SemanticAst.Reference) program.expression();

        assertThat(reference.resourceType()).isEqualTo(ResourceType.of("resource:user"));
        assertThat(reference.fieldId()).isEqualTo(FieldId.of("field:user:name"));
    }

    @Test
    @DisplayName("rejects unknown paths during compilation")
    void shouldRejectUnknownPathWhenResourceSchemaDoesNotDeclareIt() {
        ResourceSchema schema = schema("resource:user", "field:user:name", "name", LanguageType.Scalar.STRING);
        CompilerEnvironment environment = CompilerEnvironment.of(
            Map.of("object", new CompilerEnvironment.Root(schema, false))
        );

        assertThatThrownBy(() -> new EmbeddedLanguageCompiler().compile("return object.displayName;", environment))
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.BindingError);
    }

    private static SemanticAst compile(String root, ResourceSchema schema, String source) {
        return new EmbeddedLanguageCompiler().compile(
            source,
            CompilerEnvironment.of(Map.of(root, new CompilerEnvironment.Root(schema, false)))
        );
    }

    private static ResourceSchema schema(String resource, String field, String path, LanguageType type) {
        return ResourceSchema.of(
            ResourceType.of(resource),
            List.of(new Field(FieldId.of(field), FieldPath.parse(path), type, false))
        );
    }
}
