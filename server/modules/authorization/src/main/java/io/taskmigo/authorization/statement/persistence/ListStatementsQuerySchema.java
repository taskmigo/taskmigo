package io.taskmigo.authorization.statement.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.NE;

import io.taskmigo.authorization.statement.application.api.StatementQuery;
import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/// Defines the JPA Query Contract for listing Statements.
@Component
final class ListStatementsQuerySchema extends QuerySchema<StatementEntity> implements StatementQuery.List {

    ListStatementsQuerySchema() {
        super(StatementEntity.class);
    }

    @Override
    protected String operationId() {
        return "statement.query.list";
    }

    @Override
    protected Collection<QueryField<StatementEntity, ?>> staticFields() {
        return List.of(
            field(StatementEntity_.id).operators(EQ, NE).sortable(),
            field(StatementEntity_.code).operators(EQ, NE, CONTAINS).sortable(),
            field(StatementEntity_.description).operators(EQ, NE, CONTAINS).sortable(),
            field(StatementEntity_.method).operators(EQ, NE, CONTAINS).sortable(),
            field(StatementEntity_.path).operators(EQ, NE, CONTAINS).sortable()
        );
    }

    @Override
    protected Collection<QueryField<StatementEntity, ?>> runtimeFields(QueryFieldContext context) {
        return List.of();
    }
}
