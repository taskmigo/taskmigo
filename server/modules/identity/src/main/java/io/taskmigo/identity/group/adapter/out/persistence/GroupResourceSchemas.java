package io.taskmigo.identity.group.adapter.out.persistence;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.identity.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.group.GroupInfo;
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

/// Owns Group read-model schemas and their trusted JPA predicate mappings.
@Configuration(proxyBeanMethods = false)
public class GroupResourceSchemas {
    private static final TypeDescriptor STRING_TYPE = TypeDescriptor.of(String.class);
    private static final TypeDescriptor UUID_TYPE = TypeDescriptor.of(UUID.class);

    @Bean QuerySchema<GroupInfo> groupQuerySchema() {
        return schema(GroupInfo.class, List.of(field("id", UUID_TYPE, false), field("code", STRING_TYPE, false), field("displayName", STRING_TYPE, false), field("description", STRING_TYPE, true)));
    }
    @Bean QuerySchemaView groupObjectAuthorizationSchema() {
        return objectSchema(GroupInfo.class, List.of(field("id", UUID_TYPE, false), field("code", STRING_TYPE, false), field("displayName", STRING_TYPE, false), field("description", STRING_TYPE, true)));
    }
    @Bean QueryPredicateBinder<GroupInfo, GroupEntity> groupQueryPredicateBinder() { return new JpaQueryPredicateBinder<>(GroupInfo.class, GroupEntity.class, paths(), types()); }
    @Bean ObjectAuthorizationPredicateBinder<GroupInfo, GroupEntity> groupObjectAuthorizationPredicateBinder() { return new JpaObjectAuthorizationPredicateBinder<>(GroupInfo.class, GroupEntity.class, paths(), types()); }

    private static QueryField field(String path, TypeDescriptor type, boolean nullable) { return new QueryField(QueryPath.parse(path), type, nullable); }
    private static Map<String,String> paths() { return Map.of("id","id","code","code","displayName","displayName","description","description"); }
    private static Map<String,Class<?>> types() { return Map.of("id",UUID.class,"code",String.class,"displayName",String.class,"description",String.class); }
    private static QuerySchema<GroupInfo> schema(Class<GroupInfo> type, Collection<QueryField> fields) {
        List<QueryField> declared = List.copyOf(fields);
        return new QuerySchema<>() {
            @Override public Class<GroupInfo> queryType() { return type; }
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
