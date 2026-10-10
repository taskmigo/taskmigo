package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.identity.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserStatus;
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

/// Owns User read-model schemas and their trusted JPA predicate mappings.
@Configuration(proxyBeanMethods = false)
public class UserResourceSchemas {
    private static final TypeDescriptor STRING_TYPE = TypeDescriptor.of(String.class);
    private static final TypeDescriptor UUID_TYPE = TypeDescriptor.of(UUID.class);

    @Bean QuerySchema<UserInfo> userQuerySchema() {
        return schema(UserInfo.class, List.of(field("id", UUID_TYPE), field("username", STRING_TYPE), field("firstName", STRING_TYPE), field("lastName", STRING_TYPE)));
    }

    /// Registers the transitional User Object Authorization view over the shared query-schema contract.
    @Bean QuerySchemaView userObjectAuthorizationSchema() {
        return objectSchema(UserInfo.class, List.of(
            field("id", UUID_TYPE), field("username", STRING_TYPE), field("firstName", STRING_TYPE), field("lastName", STRING_TYPE),
            field("status", STRING_TYPE), new QueryField(QueryPath.parse("retainedAt"), STRING_TYPE, true)
        ));
    }

    @Bean QueryPredicateBinder<UserInfo, UserEntity> userQueryPredicateBinder() {
        return new JpaQueryPredicateBinder<>(UserInfo.class, UserEntity.class, queryPaths(), queryTypes());
    }
    @Bean ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> userObjectAuthorizationPredicateBinder() {
        return new JpaObjectAuthorizationPredicateBinder<>(UserInfo.class, UserEntity.class, objectPaths(), objectTypes());
    }

    private static QueryField field(String path, TypeDescriptor type) { return new QueryField(QueryPath.parse(path), type, false); }
    private static Map<String, String> queryPaths() { return Map.of("id","id","username","username","firstName","firstName","lastName","lastName"); }
    private static Map<String, Class<?>> queryTypes() { return Map.of("id",UUID.class,"username",String.class,"firstName",String.class,"lastName",String.class); }
    private static Map<String, String> objectPaths() { return Map.of("id","id","username","username","firstName","firstName","lastName","lastName","status","status","retainedAt","retainedAt"); }
    private static Map<String, Class<?>> objectTypes() { return Map.of("id",UUID.class,"username",String.class,"firstName",String.class,"lastName",String.class,"status",UserStatus.class,"retainedAt",String.class); }

    private static QuerySchema<UserInfo> schema(Class<UserInfo> type, Collection<QueryField> fields) {
        List<QueryField> declared = List.copyOf(fields);
        return new QuerySchema<>() {
            @Override public Class<UserInfo> queryType() { return type; }
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
