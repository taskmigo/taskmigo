package io.taskmigo.auth.resourcequery;

import io.taskmigo.auth.authorization.object.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationField;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationPath;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.auth.group.GroupEntity;
import io.taskmigo.auth.group.GroupInfo;
import io.taskmigo.auth.user.UserEntity;
import io.taskmigo.auth.user.UserInfo;
import io.taskmigo.query.QueryField;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchema;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

/// Registers query and Object Authorization contracts owned by the Identity capability.
@Configuration(proxyBeanMethods = false)
public class IdentityResourceSchemas {

    private static final ResolvableType STRING_TYPE = ResolvableType.forClass(String.class);
    private static final ResolvableType UUID_TYPE = ResolvableType.forClass(UUID.class);

    /// Registers the user collection query contract.
    @Bean
    QuerySchema<UserInfo> userQuerySchema() {
        return schema(UserInfo.class, List.of(
            field("id", UUID_TYPE), field("username", STRING_TYPE), field("firstName", STRING_TYPE), field("lastName", STRING_TYPE)
        ));
    }

    /// Registers the user Object Authorization contract.
    @Bean
    ObjectAuthorizationSchema<UserInfo> userObjectAuthorizationSchema() {
        return objectSchema(UserInfo.class, List.of(
            objectField("id", UUID_TYPE), objectField("username", STRING_TYPE),
            objectField("firstName", STRING_TYPE), objectField("lastName", STRING_TYPE)
        ));
    }

    /// Registers the group collection query contract.
    @Bean
    QuerySchema<GroupInfo> groupQuerySchema() {
        return schema(GroupInfo.class, List.of(field("id", UUID_TYPE), field("name", STRING_TYPE), nullable("description")));
    }

    /// Registers the group Object Authorization contract.
    @Bean
    ObjectAuthorizationSchema<GroupInfo> groupObjectAuthorizationSchema() {
        return objectSchema(GroupInfo.class, List.of(
            objectField("id", UUID_TYPE), objectField("name", STRING_TYPE), objectNullable("description")
        ));
    }

    /// Registers the trusted User query-to-entity mapping.
    @Bean
    QueryPredicateBinder<UserInfo, UserEntity> userQueryPredicateBinder() {
        return new JpaQueryPredicateBinder<>(UserInfo.class, UserEntity.class, userPaths(), userTypes());
    }

    /// Registers the trusted User object-policy-to-entity mapping.
    @Bean
    ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> userObjectAuthorizationPredicateBinder() {
        return new JpaObjectAuthorizationPredicateBinder<>(UserInfo.class, UserEntity.class, userPaths(), userTypes());
    }

    /// Registers the trusted Group query-to-entity mapping.
    @Bean
    QueryPredicateBinder<GroupInfo, GroupEntity> groupQueryPredicateBinder() {
        return new JpaQueryPredicateBinder<>(GroupInfo.class, GroupEntity.class, simplePaths("id", "name", "description"), simpleTypes());
    }

    /// Registers the trusted Group object-policy-to-entity mapping.
    @Bean
    ObjectAuthorizationPredicateBinder<GroupInfo, GroupEntity> groupObjectAuthorizationPredicateBinder() {
        return new JpaObjectAuthorizationPredicateBinder<>(GroupInfo.class, GroupEntity.class, simplePaths("id", "name", "description"), simpleTypes());
    }

    private static QueryField field(String path, ResolvableType type) {
        return new QueryField(QueryPath.parse(path), type, false);
    }

    private static QueryField nullable(String path) {
        return new QueryField(QueryPath.parse(path), STRING_TYPE, true);
    }

    private static ObjectAuthorizationField objectField(String path, ResolvableType type) {
        return new ObjectAuthorizationField(ObjectAuthorizationPath.parse(path), type, false);
    }

    private static ObjectAuthorizationField objectNullable(String path) {
        return new ObjectAuthorizationField(ObjectAuthorizationPath.parse(path), STRING_TYPE, true);
    }

    private static Map<String, String> userPaths() {
        return simplePaths("id", "username", "firstName", "lastName");
    }

    private static Map<String, Class<?>> userTypes() {
        return Map.of("id", UUID.class, "username", String.class, "firstName", String.class, "lastName", String.class);
    }

    private static Map<String, String> simplePaths(String... fields) {
        return java.util.Arrays.stream(fields).collect(java.util.stream.Collectors.toUnmodifiableMap(field -> field, field -> field));
    }

    private static Map<String, Class<?>> simpleTypes() {
        return Map.of("id", UUID.class, "name", String.class, "description", String.class);
    }

    private static <Q> QuerySchema<Q> schema(Class<Q> type, Collection<QueryField> fields) {
        List<QueryField> declared = List.copyOf(fields);
        return new QuerySchema<>() {
            @Override
            public Class<Q> queryType() {
                return type;
            }

            @Override
            public Optional<QueryField> field(QueryPath path) {
                return declared.stream().filter(field -> field.path().equals(path)).findFirst();
            }

            @Override
            public Collection<QueryField> fields() {
                return declared;
            }
        };
    }

    private static <Q> ObjectAuthorizationSchema<Q> objectSchema(
        Class<Q> type,
        Collection<ObjectAuthorizationField> fields
    ) {
        List<ObjectAuthorizationField> declared = List.copyOf(fields);
        return new ObjectAuthorizationSchema<>() {
            @Override
            public Class<Q> objectType() {
                return type;
            }

            @Override
            public Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath path) {
                return declared.stream().filter(field -> field.path().equals(path)).findFirst();
            }

            @Override
            public Collection<ObjectAuthorizationField> fields() {
                return declared;
            }
        };
    }
}
