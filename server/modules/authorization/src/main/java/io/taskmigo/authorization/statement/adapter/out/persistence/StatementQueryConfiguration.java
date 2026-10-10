package io.taskmigo.authorization.statement.adapter.out.persistence;

import io.taskmigo.authorization.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.query.QueryField;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchema;
import io.taskmigo.query.QuerySchemaView;
import java.util.Collection;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Provides transitional transport contracts backed by the list-statements operation schema.
@Configuration(proxyBeanMethods = false)
class StatementQueryConfiguration {

    @Bean QuerySchema<StatementInfo> statementQuerySchema(ListStatementsQuerySchema schema) { return legacy(StatementInfo.class, schema); }
    @Bean QuerySchemaView statementObjectAuthorizationSchema(ListStatementsQuerySchema schema) { return objectView(StatementInfo.class, schema); }
    @Bean QueryPredicateBinder<StatementInfo, StatementEntity> statementQueryPredicateBinder(ListStatementsQuerySchema schema) { return new JpaQueryPredicateBinder<>(StatementInfo.class, schema); }
    @Bean ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> statementObjectAuthorizationPredicateBinder(ListStatementsQuerySchema schema) { return new JpaObjectAuthorizationPredicateBinder<>(StatementInfo.class, schema); }

    private static <Q> QuerySchema<Q> legacy(Class<Q> type, QuerySchemaView view) {
        return new QuerySchema<>() {
            @Override public Class<Q> queryType() { return type; }
            @Override public Optional<QueryField> field(QueryPath path) { return this.fields().stream().filter(field -> field.path().equals(path)).findFirst(); }
            @Override public Collection<QueryField> fields() { return view.fields().stream().map(StatementQueryConfiguration::legacyField).toList(); }
            @Override public String identity() { return view.identity(); }
        };
    }

    private static QuerySchemaView objectView(Class<?> type, QuerySchemaView delegate) {
        return new QuerySchemaView() {
            @Override public String operation() { return type.getName(); }
            @Override public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) { return delegate.fields(context); }
            @Override public String identity(QueryFieldContext context) { return delegate.identity(context); }
        };
    }

    private static QueryField legacyField(QueryFieldDescriptor field) { return new QueryField(field.path(), field.type(), field.nullable(), field.operators()); }
}
