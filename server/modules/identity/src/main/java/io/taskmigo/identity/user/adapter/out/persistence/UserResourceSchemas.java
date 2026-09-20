package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.authorization.object.ObjectAuthorizationField;
import io.taskmigo.authorization.object.ObjectAuthorizationPath;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.identity.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.identity.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.user.UserInfo;
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

/// Owns User read-model schemas and their trusted JPA predicate mappings.
@Configuration(proxyBeanMethods = false)
public class UserResourceSchemas {

    private static final TypeDescriptor STRING_TYPE = TypeDescriptor.of(String.class);
    private static final TypeDescriptor UUID_TYPE = TypeDescriptor.of(UUID.class);

    /// Registers the User collection query contract.
    @Bean
    QuerySchema<UserInfo> userQuerySchema() {
        return schema(
            UserInfo.class,
            List.of(
                field("id", UUID_TYPE),
                field("username", STRING_TYPE),
                field("firstName", STRING_TYPE),
                field("lastName", STRING_TYPE)
            )
        );
    }

    /// Registers the User Object Authorization contract.
    @Bean
    ObjectAuthorizationSchema<UserInfo> userObjectAuthorizationSchema() {
        return objectSchema(
            UserInfo.class,
            List.of(
                objectField("id", UUID_TYPE),
                objectField("username", STRING_TYPE),
                objectField("firstName", STRING_TYPE),
                objectField("lastName", STRING_TYPE)
            )
        );
    }

    /// Registers the trusted User query-to-entity mapping.
    @Bean
    QueryPredicateBinder<UserInfo, UserEntity> userQueryPredicateBinder() {
        return new JpaQueryPredicateBinder<>(UserInfo.class, UserEntity.class, paths(), types());
    }

    /// Registers the trusted User object-policy-to-entity mapping.
    @Bean
    ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> userObjectAuthorizationPredicateBinder() {
        return new JpaObjectAuthorizationPredicateBinder<>(UserInfo.class, UserEntity.class, paths(), types());
    }

    private static QueryField field(String path, TypeDescriptor type) {
        return new QueryField(QueryPath.parse(path), type, false);
    }

    private static ObjectAuthorizationField objectField(String path, TypeDescriptor type) {
        return new ObjectAuthorizationField(ObjectAuthorizationPath.parse(path), type, false);
    }

    private static Map<String, String> paths() {
        return Map.of("id", "id", "username", "username", "firstName", "firstName", "lastName", "lastName");
    }

    private static Map<String, Class<?>> types() {
        return Map.of("id", UUID.class, "username", String.class, "firstName", String.class, "lastName", String.class);
    }

    private static QuerySchema<UserInfo> schema(Class<UserInfo> type, Collection<QueryField> fields) {
        List<QueryField> declared = List.copyOf(fields);
        return new QuerySchema<>() {
            @Override
            public Class<UserInfo> queryType() {
                return type;
            }

            @Override
            public Optional<QueryField> field(QueryPath path) {
                return declared
                    .stream()
                    .filter(field -> field.path().equals(path))
                    .findFirst();
            }

            @Override
            public Collection<QueryField> fields() {
                return declared;
            }
        };
    }

    private static ObjectAuthorizationSchema<UserInfo> objectSchema(
        Class<UserInfo> type,
        Collection<ObjectAuthorizationField> fields
    ) {
        List<ObjectAuthorizationField> declared = List.copyOf(fields);
        return new ObjectAuthorizationSchema<>() {
            @Override
            public Class<UserInfo> objectType() {
                return type;
            }

            @Override
            public Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath path) {
                return declared
                    .stream()
                    .filter(field -> field.path().equals(path))
                    .findFirst();
            }

            @Override
            public Collection<ObjectAuthorizationField> fields() {
                return declared;
            }
        };
    }
}
