package io.taskmigo.authorization.statement.adapter.out.persistence;

import io.taskmigo.authorization.object.ObjectAuthorizationField;
import io.taskmigo.authorization.object.ObjectAuthorizationPath;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.authorization.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.foundation.TypeDescriptor;
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

/// Registers Statement-owned Query Filtering, Object Authorization, and persistence mappings.
@Configuration(proxyBeanMethods = false)
public class StatementResourceSchemas {

    private static final TypeDescriptor STRING_TYPE = TypeDescriptor.of(String.class);
    private static final TypeDescriptor UUID_TYPE = TypeDescriptor.of(UUID.class);

    @Bean
    QuerySchema<StatementInfo> statementQuerySchema() {
        return schema(
            StatementInfo.class,
            List.of(
                field("id", UUID_TYPE),
                field("code", STRING_TYPE),
                nullable("description"),
                field("target.api.method", STRING_TYPE),
                field("target.api.path", STRING_TYPE)
            )
        );
    }

    @Bean
    ObjectAuthorizationSchema<StatementInfo> statementObjectAuthorizationSchema() {
        return objectSchema(
            StatementInfo.class,
            List.of(
                objectField("id", UUID_TYPE),
                objectField("code", STRING_TYPE),
                objectNullable("description"),
                objectField("target.api.method", STRING_TYPE),
                objectField("target.api.path", STRING_TYPE)
            )
        );
    }

    @Bean
    QueryPredicateBinder<StatementInfo, StatementEntity> statementQueryPredicateBinder() {
        return new JpaQueryPredicateBinder<>(StatementInfo.class, StatementEntity.class, paths(), types());
    }

    @Bean
    ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> statementObjectAuthorizationPredicateBinder() {
        return new JpaObjectAuthorizationPredicateBinder<>(
            StatementInfo.class,
            StatementEntity.class,
            paths(),
            types()
        );
    }

    private static QueryField field(String path, TypeDescriptor type) {
        return new QueryField(QueryPath.parse(path), type, false);
    }

    private static QueryField nullable(String path) {
        return new QueryField(QueryPath.parse(path), STRING_TYPE, true);
    }

    private static ObjectAuthorizationField objectField(String path, TypeDescriptor type) {
        return new ObjectAuthorizationField(ObjectAuthorizationPath.parse(path), type, false);
    }

    private static ObjectAuthorizationField objectNullable(String path) {
        return new ObjectAuthorizationField(ObjectAuthorizationPath.parse(path), STRING_TYPE, true);
    }

    private static Map<String, String> paths() {
        return Map.of(
            "id",
            "id",
            "code",
            "code",
            "description",
            "description",
            "target.api.method",
            "method",
            "target.api.path",
            "path"
        );
    }

    private static Map<String, Class<?>> types() {
        return Map.of(
            "id",
            UUID.class,
            "code",
            String.class,
            "description",
            String.class,
            "target.api.method",
            String.class,
            "target.api.path",
            String.class
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
