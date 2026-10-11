package io.taskmigo.identity.user.adapter.out.persistence;

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

/// Defines the JPA query surface used to resolve a User for deletion.
@Component
final class DeleteUserQuerySchema extends QuerySchema<UserEntity> {

    DeleteUserQuerySchema() {
        super(UserEntity.class);
    }

    @Override
    protected String operationId() {
        return "identity.users.delete";
    }

    @Override
    protected Collection<QueryField<UserEntity, ?>> staticFields() {
        return List.of(
            field(UserEntity_.id).operators(EQ, NE),
            field(UserEntity_.username).operators(EQ, NE, CONTAINS),
            field(UserEntity_.status).operators(EQ, NE),
            field(UserEntity_.retainedAt).operators(EQ, NE, GT, GTE, LT, LTE)
        );
    }

    @Override
    protected Collection<QueryField<UserEntity, ?>> runtimeFields(QueryFieldContext context) {
        return List.of();
    }
}
