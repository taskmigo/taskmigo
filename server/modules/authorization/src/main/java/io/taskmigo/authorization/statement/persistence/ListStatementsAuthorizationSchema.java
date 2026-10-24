package io.taskmigo.authorization.statement.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.NE;

import io.taskmigo.authorization.statement.application.api.StatementAuthorization;
import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/// Defines the Object Authorization field surface for listing Statements.
@Component
final class ListStatementsAuthorizationSchema extends QuerySchema<StatementEntity> implements StatementAuthorization.List {

    ListStatementsAuthorizationSchema() {
        super(StatementEntity.class);
    }

    @Override
    protected String operationId() {
        return "statement.authorization.list";
    }

    @Override
    protected Collection<QueryField<StatementEntity, ?>> staticFields() {
        return List.of(
            field(StatementEntity_.id).operators(EQ, NE),
            field(StatementEntity_.code).operators(EQ, NE, CONTAINS),
            field(StatementEntity_.description).operators(EQ, NE, CONTAINS),
            field(StatementEntity_.method).operators(EQ, NE, CONTAINS),
            field(StatementEntity_.path).operators(EQ, NE, CONTAINS)
        );
    }

    @Override
    protected Collection<QueryField<StatementEntity, ?>> runtimeFields(QueryFieldContext context) {
        return List.of();
    }
}
