package io.taskmigo.authorization.role.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.NE;

import io.taskmigo.authorization.role.application.api.RoleQuery;
import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/// Defines the JPA Query Contract for listing Roles.
@Component
final class ListRolesQuerySchema extends QuerySchema<RoleEntity> implements RoleQuery.List {

    ListRolesQuerySchema() {
        super(RoleEntity.class);
    }

    @Override
    protected String operationId() {
        return "role.query.list";
    }

    @Override
    protected Collection<QueryField<RoleEntity, ?>> staticFields() {
        return List.of(
            field(RoleEntity_.id).operators(EQ, NE).sortable(),
            field(RoleEntity_.code).operators(EQ, NE, CONTAINS).sortable(),
            field(RoleEntity_.displayName).operators(EQ, NE, CONTAINS).sortable(),
            field(RoleEntity_.description).operators(EQ, NE, CONTAINS).sortable()
        );
    }

    @Override
    protected Collection<QueryField<RoleEntity, ?>> runtimeFields(QueryFieldContext context) {
        return List.of();
    }
}
