package io.taskmigo.authorization.role.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.NE;

import io.taskmigo.authorization.role.application.api.RoleAuthorization;
import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/// Defines the Object Authorization field surface for listing Roles.
@Component
final class ListRolesAuthorizationSchema extends QuerySchema<RoleEntity> implements RoleAuthorization.List {

    ListRolesAuthorizationSchema() {
        super(RoleEntity.class);
    }

    @Override
    protected String operationId() {
        return "role.authorization.list";
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
