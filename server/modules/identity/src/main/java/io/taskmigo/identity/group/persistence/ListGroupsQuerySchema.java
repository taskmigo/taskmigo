package io.taskmigo.identity.group.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.NE;

import io.taskmigo.identity.group.application.api.GroupQuery;
import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/// Defines the JPA Query Contract for listing Groups.
@Component
final class ListGroupsQuerySchema extends QuerySchema<GroupEntity> implements GroupQuery.List {

    ListGroupsQuerySchema() {
        super(GroupEntity.class);
    }

    @Override
    protected String operationId() {
        return "group.query.list";
    }

    @Override
    protected Collection<QueryField<GroupEntity, ?>> staticFields() {
        return List.of(
            field(GroupEntity_.id).operators(EQ, NE).sortable(),
            field(GroupEntity_.code).operators(EQ, NE, CONTAINS).sortable(),
            field(GroupEntity_.displayName).operators(EQ, NE, CONTAINS).sortable(),
            field(GroupEntity_.description).operators(EQ, NE, CONTAINS).sortable()
        );
    }

    @Override
    protected Collection<QueryField<GroupEntity, ?>> runtimeFields(QueryFieldContext context) {
        return List.of();
    }
}
