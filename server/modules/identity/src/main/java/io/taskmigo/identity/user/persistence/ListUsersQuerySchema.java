package io.taskmigo.identity.user.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.GT;
import static io.taskmigo.query.QueryOperator.GTE;
import static io.taskmigo.query.QueryOperator.LT;
import static io.taskmigo.query.QueryOperator.LTE;
import static io.taskmigo.query.QueryOperator.NE;

import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/// Defines the JPA query surface for the list-users operation.
@Component
final class ListUsersQuerySchema extends QuerySchema<UserEntity> {

    ListUsersQuerySchema() {
        super(UserEntity.class);
    }

    @Override
    protected String operationId() {
        return "identity.users.list";
    }

    @Override
    protected Collection<QueryField<UserEntity, ?>> staticFields() {
        return List.of(
            field(UserEntity_.id).operators(EQ, NE).sortable(),
            field(UserEntity_.username).operators(EQ, NE, CONTAINS).sortable(),
            field(UserEntity_.firstName).operators(EQ, NE, CONTAINS).sortable(),
            field(UserEntity_.lastName).operators(EQ, NE, CONTAINS).sortable(),
            field(UserEntity_.status).operators(EQ, NE).sortable(),
            field(UserEntity_.retainedAt).operators(EQ, NE, GT, GTE, LT, LTE).sortable()
        );
    }

    @Override
    protected Collection<QueryField<UserEntity, ?>> runtimeFields(QueryFieldContext context) {
        return List.of();
    }
}
