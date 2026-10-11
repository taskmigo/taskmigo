package io.taskmigo.identity.user.adapter.out.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.NE;

import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/// Defines the JPA query surface used to resolve a User while updating direct Statement assignments.
@Component
final class UpdateUserStatementsQuerySchema extends QuerySchema<UserEntity> {

    UpdateUserStatementsQuerySchema() {
        super(UserEntity.class);
    }

    @Override
    protected String operationId() {
        return "identity.users.update-statements";
    }

    @Override
    protected Collection<QueryField<UserEntity, ?>> staticFields() {
        return List.of(
            field(UserEntity_.id).operators(EQ, NE),
            field(UserEntity_.username).operators(EQ, NE, CONTAINS),
            field(UserEntity_.status).operators(EQ, NE)
        );
    }

    @Override
    protected Collection<QueryField<UserEntity, ?>> runtimeFields(QueryFieldContext context) {
        return List.of();
    }
}
