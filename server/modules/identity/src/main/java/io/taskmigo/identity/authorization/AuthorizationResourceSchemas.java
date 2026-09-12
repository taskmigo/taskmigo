package io.taskmigo.identity.authorization;

import io.taskmigo.authorization.object.ObjectAuthorizationField;
import io.taskmigo.authorization.object.ObjectAuthorizationPath;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.identity.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.identity.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.persistence.role.RoleEntity;
import io.taskmigo.identity.persistence.statement.StatementEntity;
import io.taskmigo.query.QueryField;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchema;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

/// Registers query and Object Authorization contracts for Authorization-owned resources.
@Configuration(proxyBeanMethods = false)
public class AuthorizationResourceSchemas {

    private static final ResolvableType STRING_TYPE = ResolvableType.forClass(String.class);
    private static final ResolvableType UUID_TYPE = ResolvableType.forClass(UUID.class);

    /// Registers the role collection query contract.
    @Bean
    QuerySchema<RoleInfo> roleQuerySchema() {
        return schema(
            RoleInfo.class,
            List.of(field("id", UUID_TYPE), field("name", STRING_TYPE), nullable("description"))
        );
    }

    /// Registers the role Object Authorization contract.
    @Bean
    ObjectAuthorizationSchema<RoleInfo> roleObjectAuthorizationSchema() {
        return objectSchema(
            RoleInfo.class,
            List.of(objectField("id", UUID_TYPE), objectField("name", STRING_TYPE), objectNullable("description"))
        );
    }

    /// Registers the statement collection query contract, including composed API target paths.
    @Bean
    QuerySchema<StatementInfo> statementQuerySchema() {
        return schema(
            StatementInfo.class,
            List.of(
                field("id", UUID_TYPE),
                field("name", STRING_TYPE),
                nullable("description"),
                field("target.api.method", STRING_TYPE),
                field("target.api.path", STRING_TYPE)
            )
        );
    }

    /// Registers the Statement Object Authorization contract.
    @Bean
    ObjectAuthorizationSchema<StatementInfo> statementObjectAuthorizationSchema() {
        return objectSchema(
            StatementInfo.class,
            List.of(
                objectField("id", UUID_TYPE),
                objectField("name", STRING_TYPE),
                objectNullable("description"),
                objectField("target.api.method", STRING_TYPE),
                objectField("target.api.path", STRING_TYPE)
            )
        );
    }

    /// Registers the trusted Role query-to-entity mapping.
    @Bean
    QueryPredicateBinder<RoleInfo, RoleEntity> roleQueryPredicateBinder() {
        return new JpaQueryPredicateBinder<>(
            RoleInfo.class,
            RoleEntity.class,
            simplePaths("id", "name", "description"),
            simpleTypes()
        );
    }

    /// Registers the trusted Role object-policy-to-entity mapping.
    @Bean
    ObjectAuthorizationPredicateBinder<RoleInfo, RoleEntity> roleObjectAuthorizationPredicateBinder() {
        return new JpaObjectAuthorizationPredicateBinder<>(
            RoleInfo.class,
            RoleEntity.class,
            simplePaths("id", "name", "description"),
            simpleTypes()
        );
    }

    /// Registers the trusted Statement query-to-entity mapping.
    @Bean
    QueryPredicateBinder<StatementInfo, StatementEntity> statementQueryPredicateBinder() {
        return new JpaQueryPredicateBinder<>(
            StatementInfo.class,
            StatementEntity.class,
            statementPaths(),
            statementTypes()
        );
    }

    /// Registers the trusted Statement object-policy-to-entity mapping.
    @Bean
    ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> statementObjectAuthorizationPredicateBinder() {
        return new JpaObjectAuthorizationPredicateBinder<>(
            StatementInfo.class,
            StatementEntity.class,
            statementPaths(),
            statementTypes()
        );
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

    private static Map<String, String> simplePaths(String... fields) {
        return Arrays.stream(fields).collect(Collectors.toUnmodifiableMap(field -> field, field -> field));
    }

    private static Map<String, Class<?>> simpleTypes() {
        return Map.of("id", UUID.class, "name", String.class, "description", String.class);
    }

    private static Map<String, Class<?>> statementTypes() {
        return Map.of(
            "id",
            UUID.class,
            "name",
            String.class,
            "description",
            String.class,
            "target.api.method",
            String.class,
            "target.api.path",
            String.class
        );
    }

    private static Map<String, String> statementPaths() {
        return Map.of(
            "id",
            "id",
            "name",
            "name",
            "description",
            "description",
            "target.api.method",
            "method",
            "target.api.path",
            "path"
        );
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
