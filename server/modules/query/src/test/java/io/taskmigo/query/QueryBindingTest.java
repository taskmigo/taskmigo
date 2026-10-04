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
import io.taskmigo.query.model.QueryExpression;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryBindingTest {

    private static final ResourceType TICKET = ResourceType.of("resource:ticket");
    private static final FieldId PRIORITY = FieldId.of("field:ticket:priority");
    private static final Field PRIORITY_FIELD = new Field(
        PRIORITY,
        FieldPath.parse("priority"),
        LanguageType.Scalar.NUMBER,
        true
    );
    private static final ResourceSchema SCHEMA = ResourceSchema.of(TICKET, List.of(PRIORITY_FIELD));

    @Test
    @DisplayName("compiles runtime-created fields into query references with stable field identities")
    void shouldCompileStableFieldIdentityWhenSchemaIsCreatedAtRuntime() {
        QueryBinding<TicketQuery> binding = binding(
            SCHEMA.fingerprint(),
            new QueryFieldBinding(PRIORITY, QueryPath.parse("priorityValue"), Number.class, Set.of(QueryOperator.GE))
        );

        QueryPredicate<TicketQuery> predicate = new FilterByCompiler().compile(SCHEMA, binding, "object.priority >= 3");
        QueryExpression.Binary expression = (QueryExpression.Binary) QueryPredicateFactory.model(
            predicate
        ).expression();
        QueryExpression.Reference reference = (QueryExpression.Reference) expression.left();

        assertThat(reference.fieldId()).isEqualTo(PRIORITY);
    }

    @Test
    @DisplayName("rejects a query binding compiled for another schema fingerprint")
    void shouldRejectBindingWhenSchemaFingerprintDoesNotMatch() {
        ResourceSchema changed = ResourceSchema.of(
            TICKET,
            List.of(new Field(PRIORITY, FieldPath.parse("priority"), LanguageType.Scalar.NUMBER, false))
        );
        QueryBinding<TicketQuery> binding = binding(
            SCHEMA.fingerprint(),
            new QueryFieldBinding(PRIORITY, QueryPath.parse("priorityValue"), Number.class, Set.of(QueryOperator.GE))
        );

        assertThatThrownBy(() -> new FilterByCompiler().compile(changed, binding, "object.priority >= 3"))
            .isInstanceOf(FilterByException.class)
            .hasMessageContaining("Invalid filterBy expression");
    }

    @Test
    @DisplayName("rejects a semantic field that has no execution binding")
    void shouldRejectFieldWhenExecutionBindingIsMissing() {
        QueryBinding<TicketQuery> binding = binding(SCHEMA.fingerprint());

        assertThatThrownBy(() -> new FilterByCompiler().compile(SCHEMA, binding, "object.priority >= 3"))
            .isInstanceOf(FilterByException.class)
            .hasMessageContaining("Invalid filterBy expression");
    }

    @Test
    @DisplayName("rejects an operator unsupported by the execution binding")
    void shouldRejectOperatorWhenExecutionBindingDoesNotSupportIt() {
        QueryBinding<TicketQuery> binding = binding(
            SCHEMA.fingerprint(),
            new QueryFieldBinding(PRIORITY, QueryPath.parse("priorityValue"), Number.class, Set.of(QueryOperator.EQ))
        );

        assertThatThrownBy(() -> new FilterByCompiler().compile(SCHEMA, binding, "object.priority >= 3"))
            .isInstanceOf(FilterByException.class)
            .hasMessageContaining("Invalid filterBy expression");
    }

    @Test
    @DisplayName("does not fall back to matching a display path from another field identity")
    void shouldRejectFieldWhenOnlyDisplayPathWouldMatch() {
        QueryBinding<TicketQuery> binding = binding(
            SCHEMA.fingerprint(),
            new QueryFieldBinding(
                FieldId.of("field:other:priority"),
                QueryPath.parse("priorityValue"),
                Number.class,
                Set.of(QueryOperator.GE)
            )
        );

        assertThatThrownBy(() -> new FilterByCompiler().compile(SCHEMA, binding, "object.priority >= 3")).isInstanceOf(
            FilterByException.class
        );
    }

    @Test
    @DisplayName("compiles blank input as an always true predicate after compatibility validation")
    void shouldCompileAlwaysTrueWhenInputIsBlankAndBindingIsCompatible() {
        QueryBinding<TicketQuery> binding = binding(SCHEMA.fingerprint());

        QueryPredicate<TicketQuery> predicate = new FilterByCompiler().compile(SCHEMA, binding, " ");

        assertThat(predicate.isAlwaysTrue()).isTrue();
    }

    private static QueryBinding<TicketQuery> binding(SchemaFingerprint fingerprint, QueryFieldBinding... fields) {
        List<QueryFieldBinding> declarations = List.of(fields);
        return new QueryBinding<>() {
            @Override
            public Class<TicketQuery> queryType() {
                return TicketQuery.class;
            }

            @Override
            public ResourceType resourceType() {
                return TICKET;
            }

            @Override
            public SchemaFingerprint schemaFingerprint() {
                return fingerprint;
            }

            @Override
            public Optional<QueryFieldBinding> field(FieldId id) {
                return declarations
                    .stream()
                    .filter(field -> field.id().equals(id))
                    .findFirst();
            }

            @Override
            public Collection<QueryFieldBinding> fields() {
                return declarations;
            }
        };
    }

    private interface TicketQuery {}
}
