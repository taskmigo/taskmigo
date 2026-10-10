package io.taskmigo.authorization.statement.adapter.out.persistence;

import io.taskmigo.authorization.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.query.QueryField;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchema;
import io.taskmigo.query.QuerySchemaView;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Registers Statement-owned Query Filtering, Object Authorization, and persistence mappings.
@Configuration(proxyBeanMethods = false)
public class StatementResourceSchemas {
    private static final TypeDescriptor STRING_TYPE = TypeDescriptor.of(String.class);
    private static final TypeDescriptor UUID_TYPE = TypeDescriptor.of(UUID.class);

    @Bean QuerySchema<StatementInfo> statementQuerySchema() { return schema(StatementInfo.class, fields()); }
    @Bean QuerySchemaView statementObjectAuthorizationSchema() { return objectSchema(StatementInfo.class, fields()); }
    @Bean QueryPredicateBinder<StatementInfo, StatementEntity> statementQueryPredicateBinder() { return new JpaQueryPredicateBinder<>(StatementInfo.class, StatementEntity.class, paths(), types()); }
    @Bean ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> statementObjectAuthorizationPredicateBinder() { return new JpaObjectAuthorizationPredicateBinder<>(StatementInfo.class, StatementEntity.class, paths(), types()); }

    private static List<QueryField> fields() { return List.of(field("id", UUID_TYPE), field("code", STRING_TYPE), nullable("description"), field("target.api.method", STRING_TYPE), field("target.api.path", STRING_TYPE)); }
    private static QueryField field(String path, TypeDescriptor type) { return new QueryField(QueryPath.parse(path), type, false); }
    private static QueryField nullable(String path) { return new QueryField(QueryPath.parse(path), STRING_TYPE, true); }
    private static Map<String,String> paths() { return Map.of("id","id","code","code","description","description","target.api.method","method","target.api.path","path"); }
    private static Map<String,Class<?>> types() { return Map.of("id",UUID.class,"code",String.class,"description",String.class,"target.api.method",String.class,"target.api.path",String.class); }
    private static <Q> QuerySchema<Q> schema(Class<Q> type, Collection<QueryField> fields) {
        List<QueryField> declared = List.copyOf(fields);
        return new QuerySchema<>() {
            @Override public Class<Q> queryType() { return type; }
            @Override public Optional<QueryField> field(QueryPath path) { return declared.stream().filter(field -> field.path().equals(path)).findFirst(); }
            @Override public Collection<QueryField> fields() { return declared; }
        };
    }
    private static QuerySchemaView objectSchema(Class<?> type, Collection<QueryField> fields) {
        List<QueryFieldDescriptor> declared = fields.stream().map(field -> new QueryFieldDescriptor(field.path(), field.type(), field.nullable(), field.operators())).toList();
        return new QuerySchemaView() {
            @Override public String operation() { return type.getName(); }
            @Override public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) { return declared; }
        };
    }
}
