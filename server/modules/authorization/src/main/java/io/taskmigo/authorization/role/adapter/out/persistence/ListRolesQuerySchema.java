package io.taskmigo.authorization.role.adapter.out.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.NE;

import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/// Defines the JPA query surface for the list-roles operation.
@Component
final class ListRolesQuerySchema extends QuerySchema<RoleEntity> {

    ListRolesQuerySchema() {
        super(RoleEntity.class);
    }

    @Override
    protected String operationId() {
        return "access-control.roles.list";
    }

    @Override
    protected Collection<QueryField<RoleEntity, ?>> staticFields() {
        return List.of(
            field(RoleEntity_.id).operators(EQ, NE),
            field(RoleEntity_.code).operators(EQ, NE, CONTAINS),
            field(RoleEntity_.displayName).operators(EQ, NE, CONTAINS),
            field(RoleEntity_.description).operators(EQ, NE, CONTAINS)
        );
    }

    @Override
    protected Collection<QueryField<RoleEntity, ?>> runtimeFields(QueryFieldContext context) {
        return List.of();
    }
}
