package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.language.Field;
import io.taskmigo.language.FieldId;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaFingerprint;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FilterByCompilerTest {

    private final Surface<CustomerQuery> customer = surface(
        CustomerQuery.class,
        "resource:customer",
        declaration("field:customer:name", "name", LanguageType.Scalar.STRING, false, Set.of(QueryOperator.EQ))
    );

    @Test
    @DisplayName("should return an always-true predicate when filterBy is absent")
    void shouldReturnAlwaysTrueWhenFilterByIsAbsent() {
        QueryPredicate<CustomerQuery> result = new FilterByCompiler().compile(
            this.customer.schema(),
            this.customer.binding(),
            null
        );

        assertThat(result.isAlwaysTrue()).isTrue();
    }

    @Test
    @DisplayName("should compile a registered path when filterBy is boolean")
    void shouldCompileRegisteredPathWhenFilterByIsBoolean() {
        QueryPredicate<CustomerQuery> result = new FilterByCompiler().compile(
            this.customer.schema(),
            this.customer.binding(),
            "object.name == \"Phong\""
        );

        assertThat(result.isAlwaysTrue()).isFalse();
        assertThat(result.isAlwaysFalse()).isFalse();
    }

    @Test
    @DisplayName("should reject an unregistered path when filterBy references persistence data")
    void shouldRejectUnregisteredPathWhenFilterByReferencesPersistenceData() {
        assertThatThrownBy(() ->
            new FilterByCompiler().compile(
                this.customer.schema(),
                this.customer.binding(),
                "object.email == \"a@example.com\""
            )
        ).isInstanceOf(FilterByException.class);
    }

    @Test
    @DisplayName("should compile a compound filter when scalar fields are combined")
    void shouldCompileCompoundFilterWhenScalarFieldsAreCombined() {
        Surface<CustomerQuery> compound = surface(
            CustomerQuery.class,
            "resource:compound-customer",
            declaration("field:customer:name", "name", LanguageType.Scalar.STRING, false, Set.of(QueryOperator.EQ)),
            declaration("field:customer:email", "email", LanguageType.Scalar.STRING, false, Set.of(QueryOperator.EQ))
        );

        QueryPredicate<CustomerQuery> result = new FilterByCompiler().compile(
            compound.schema(),
            compound.binding(),
            "object.name == \"Phong\" && object.email == \"p@example.com\""
        );

        assertThat(result.isAlwaysTrue()).isFalse();
        assertThat(result.isAlwaysFalse()).isFalse();
    }

    @Test
    @DisplayName("should use API-visible nested paths for Statement filtering")
    void shouldCompileApiVisibleStatementPathWhenFilterReferencesTarget() {
        Surface<StatementQuery> statement = surface(
            StatementQuery.class,
            "resource:statement",
            declaration(
                "field:statement:method",
                "target.api.method",
                LanguageType.Scalar.STRING,
                false,
                Set.of(QueryOperator.EQ)
            ),
            declaration(
                "field:statement:path",
                "target.api.path",
                LanguageType.Scalar.STRING,
                false,
                Set.of(QueryOperator.EQ)
            )
        );

        QueryPredicate<StatementQuery> result = new FilterByCompiler().compile(
            statement.schema(),
            statement.binding(),
            "object.target.api.method == \"GET\""
        );

        assertThat(result.isAlwaysTrue()).isFalse();
        assertThatThrownBy(() ->
            new FilterByCompiler().compile(statement.schema(), statement.binding(), "object.method == \"GET\"")
        ).isInstanceOf(FilterByException.class);
    }

    @Test
    @DisplayName("should reject predicate composition when schema identities differ")
    void shouldRejectPredicateCompositionWhenSchemaIdentitiesDiffer() {
        QueryPredicate<CustomerQuery> left = new FilterByCompiler().compile(
            this.customer.schema(),
            this.customer.binding(),
            "object.name == \"Phong\""
        );
        Surface<CustomerQuery> changed = surface(
            CustomerQuery.class,
            "resource:customer",
            declaration("field:customer:name", "name", LanguageType.Scalar.STRING, true, Set.of(QueryOperator.EQ))
        );
        QueryPredicate<CustomerQuery> right = new FilterByCompiler().compile(
            changed.schema(),
            changed.binding(),
            "object.name == \"Phong\""
        );

        assertThatThrownBy(() -> QueryPredicates.standard().and(left, right))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("incompatible");
    }

    @SafeVarargs
    private static <Q> Surface<Q> surface(Class<Q> queryType, String resource, Declaration... declarations) {
        ResourceType resourceType = ResourceType.of(resource);
        List<Field> fields = Arrays.stream(declarations).map(Declaration::field).toList();
        ResourceSchema schema = ResourceSchema.of(resourceType, fields);
        List<QueryFieldBinding> bindings = Arrays.stream(declarations).map(Declaration::binding).toList();
        QueryBinding<Q> binding = new QueryBinding<>() {
            @Override
            public Class<Q> queryType() {
                return queryType;
            }

            @Override
            public ResourceType resourceType() {
                return resourceType;
            }

            @Override
            public SchemaFingerprint schemaFingerprint() {
                return schema.fingerprint();
            }

            @Override
            public Optional<QueryFieldBinding> field(FieldId id) {
                return bindings
                    .stream()
                    .filter(field -> field.id().equals(id))
                    .findFirst();
            }

            @Override
            public Collection<QueryFieldBinding> fields() {
                return bindings;
            }
        };
        return new Surface<>(schema, binding);
    }

    private static Declaration declaration(
        String id,
        String path,
        LanguageType type,
        boolean nullable,
        Set<QueryOperator> operators
    ) {
        FieldId fieldId = FieldId.of(id);
        return new Declaration(
            new Field(fieldId, FieldPath.parse(path), type, nullable),
            new QueryFieldBinding(fieldId, QueryPath.parse(path), executionType(type), operators)
        );
    }

    private static Class<?> executionType(LanguageType type) {
        if (type == LanguageType.Scalar.STRING) {
            return String.class;
        }
        if (type == LanguageType.Scalar.NUMBER) {
            return Number.class;
        }
        if (type == LanguageType.Scalar.BOOL) {
            return Boolean.class;
        }
        return Object.class;
    }

    private record Declaration(Field field, QueryFieldBinding binding) {}

    private record Surface<Q>(ResourceSchema schema, QueryBinding<Q> binding) {}

    private static final class CustomerQuery {}

    private static final class StatementQuery {}
}
