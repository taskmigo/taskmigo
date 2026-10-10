package io.taskmigo.authorization.role.adapter.out.persistence;

import io.taskmigo.authorization.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.query.QueryField;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchema;
import io.taskmigo.query.QuerySchemaView;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Registers Role-owned Query Filtering, Object Authorization, and persistence mappings.
@Configuration(proxyBeanMethods = false)
public class RoleResourceSchemas {
    private static final TypeDescriptor STRING_TYPE = TypeDescriptor.of(String.class);
    private static final TypeDescriptor UUID_TYPE = TypeDescriptor.of(UUID.class);

    @Bean QuerySchema<RoleInfo> roleQuerySchema() { return schema(RoleInfo.class, List.of(field("id", UUID_TYPE), field("code", STRING_TYPE), field("displayName", STRING_TYPE), nullable("description"))); }
    @Bean QuerySchemaView roleObjectAuthorizationSchema() { return objectSchema(RoleInfo.class, List.of(field("id", UUID_TYPE), field("code", STRING_TYPE), field("displayName", STRING_TYPE), nullable("description"))); }
    @Bean QueryPredicateBinder<RoleInfo, RoleEntity> roleQueryPredicateBinder() { return new JpaQueryPredicateBinder<>(RoleInfo.class, RoleEntity.class, simplePaths("id","code","displayName","description"), simpleTypes()); }
    @Bean ObjectAuthorizationPredicateBinder<RoleInfo, RoleEntity> roleObjectAuthorizationPredicateBinder() { return new JpaObjectAuthorizationPredicateBinder<>(RoleInfo.class, RoleEntity.class, simplePaths("id","code","displayName","description"), simpleTypes()); }

    private static QueryField field(String path, TypeDescriptor type) { return new QueryField(QueryPath.parse(path), type, false); }
    private static QueryField nullable(String path) { return new QueryField(QueryPath.parse(path), STRING_TYPE, true); }
    private static Map<String,String> simplePaths(String... fields) { return Arrays.stream(fields).collect(Collectors.toUnmodifiableMap(field -> field, field -> field)); }
    private static Map<String,Class<?>> simpleTypes() { return Map.of("id",UUID.class,"code",String.class,"displayName",String.class,"description",String.class); }
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
