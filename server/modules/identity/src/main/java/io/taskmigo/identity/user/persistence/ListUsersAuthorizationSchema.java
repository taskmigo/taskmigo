package io.taskmigo.identity.user.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.GT;
import static io.taskmigo.query.QueryOperator.GTE;
import static io.taskmigo.query.QueryOperator.LT;
import static io.taskmigo.query.QueryOperator.LTE;
import static io.taskmigo.query.QueryOperator.NE;

import io.taskmigo.identity.user.application.api.UserAuthorization;
import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/// Defines the Object Authorization field surface for listing Users.
@Component
final class ListUsersAuthorizationSchema extends QuerySchema<UserEntity> implements UserAuthorization.List {

    ListUsersAuthorizationSchema() {
        super(UserEntity.class);
    }

    @Override
    protected String operationId() {
        return "user.authorization.list";
    }

    @Override
    protected Collection<QueryField<UserEntity, ?>> staticFields() {
        return List.of(
            field(UserEntity_.id).operators(EQ, NE),
            field(UserEntity_.username).operators(EQ, NE, CONTAINS),
            field(UserEntity_.firstName).operators(EQ, NE, CONTAINS),
            field(UserEntity_.lastName).operators(EQ, NE, CONTAINS),
            field(UserEntity_.status).operators(EQ, NE),
            field(UserEntity_.retainedAt).operators(EQ, NE, GT, GTE, LT, LTE)
        );
    }

    @Override
    protected Collection<QueryField<UserEntity, ?>> runtimeFields(QueryFieldContext context) {
        return List.of();
    }
}
