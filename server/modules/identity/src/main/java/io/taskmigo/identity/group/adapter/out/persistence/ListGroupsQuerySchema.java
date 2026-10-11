package io.taskmigo.identity.group.adapter.out.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.NE;

import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/// Defines the JPA query surface for the list-groups operation.
@Component
final class ListGroupsQuerySchema extends QuerySchema<GroupEntity> {

    ListGroupsQuerySchema() {
        super(GroupEntity.class);
    }

    @Override
    protected String operationId() {
        return "identity.groups.list";
    }

    @Override
    protected Collection<QueryField<GroupEntity, ?>> staticFields() {
        return List.of(
            field(GroupEntity_.id).operators(EQ, NE),
            field(GroupEntity_.code).operators(EQ, NE, CONTAINS),
            field(GroupEntity_.displayName).operators(EQ, NE, CONTAINS),
            field(GroupEntity_.description).operators(EQ, NE, CONTAINS)
        );
    }

    @Override
    protected Collection<QueryField<GroupEntity, ?>> runtimeFields(QueryFieldContext context) {
        return List.of();
    }
}
