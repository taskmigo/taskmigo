package io.taskmigo.identity.group.adapter.out.persistence;

import io.taskmigo.authorization.object.ObjectAuthorizationField;
import io.taskmigo.authorization.object.ObjectAuthorizationPath;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.identity.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.group.GroupInfo;
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

/// Owns Group read-model schemas and their trusted JPA predicate mappings.
@Configuration(proxyBeanMethods = false)
public class GroupResourceSchemas {

    private static final TypeDescriptor STRING_TYPE = TypeDescriptor.of(String.class);
    private static final TypeDescriptor UUID_TYPE = TypeDescriptor.of(UUID.class);

    /// Registers the Group collection query contract.
    @Bean
    QuerySchema<GroupInfo> groupQuerySchema() {
        return schema(
            GroupInfo.class,
            List.of(
                field("id", UUID_TYPE, false),
                field("code", STRING_TYPE, false),
                field("displayName", STRING_TYPE, false),
                field("description", STRING_TYPE, true)
            )
        );
    }

    /// Registers the Group Object Authorization contract.
    @Bean
    ObjectAuthorizationSchema<GroupInfo> groupObjectAuthorizationSchema() {
        return objectSchema(
            GroupInfo.class,
            List.of(
                objectField("id", UUID_TYPE, false),
                objectField("code", STRING_TYPE, false),
                objectField("displayName", STRING_TYPE, false),
                objectField("description", STRING_TYPE, true)
            )
        );
    }

    /// Registers the trusted Group query-to-entity mapping.
    @Bean
    QueryPredicateBinder<GroupInfo, GroupEntity> groupQueryPredicateBinder() {
        return new JpaQueryPredicateBinder<>(GroupInfo.class, GroupEntity.class, paths(), types());
    }

    /// Registers the trusted Group object-policy-to-entity mapping.
    @Bean
    ObjectAuthorizationPredicateBinder<GroupInfo, GroupEntity> groupObjectAuthorizationPredicateBinder() {
        return new JpaObjectAuthorizationPredicateBinder<>(GroupInfo.class, GroupEntity.class, paths(), types());
    }

    private static QueryField field(String path, TypeDescriptor type, boolean nullable) {
        return new QueryField(QueryPath.parse(path), type, nullable);
    }

    private static ObjectAuthorizationField objectField(String path, TypeDescriptor type, boolean nullable) {
        return new ObjectAuthorizationField(ObjectAuthorizationPath.parse(path), type, nullable);
    }

    private static Map<String, String> paths() {
        return Map.of("id", "id", "code", "code", "displayName", "displayName", "description", "description");
    }

    private static Map<String, Class<?>> types() {
        return Map.of("id", UUID.class, "code", String.class, "displayName", String.class, "description", String.class);
    }

    private static QuerySchema<GroupInfo> schema(Class<GroupInfo> type, Collection<QueryField> fields) {
        List<QueryField> declared = List.copyOf(fields);
        return new QuerySchema<>() {
            @Override
            public Class<GroupInfo> queryType() {
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

    private static ObjectAuthorizationSchema<GroupInfo> objectSchema(
        Class<GroupInfo> type,
        Collection<ObjectAuthorizationField> fields
    ) {
        List<ObjectAuthorizationField> declared = List.copyOf(fields);
        return new ObjectAuthorizationSchema<>() {
            @Override
            public Class<GroupInfo> objectType() {
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
