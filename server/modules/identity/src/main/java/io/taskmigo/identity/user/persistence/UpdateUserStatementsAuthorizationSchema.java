package io.taskmigo.identity.user.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.NE;

import io.taskmigo.identity.user.application.api.UserAuthorization;
import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/// Defines the Object Authorization field surface for replacing one User's direct Statement assignments.
@Component
final class UpdateUserStatementsAuthorizationSchema extends QuerySchema<UserEntity>
    implements UserAuthorization.UpdateStatements
{

    UpdateUserStatementsAuthorizationSchema() {
        super(UserEntity.class);
    }

    @Override
    protected String operationId() {
        return "user.authorization.update-statements";
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
