package io.taskmigo.authorization.role.adapter.out.persistence;

import io.taskmigo.authorization.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationFieldBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationOperator;
import io.taskmigo.authorization.object.StaticObjectAuthorizationBinding;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.language.Field;
import io.taskmigo.language.FieldId;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceType;
import io.taskmigo.query.QueryBinding;
import io.taskmigo.query.QueryFieldBinding;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.StaticQueryBinding;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Registers Role semantic fields and their query and Object Authorization execution bindings.
@Configuration(proxyBeanMethods = false)
public class RoleResourceSchemas {

    private static final ResourceType TYPE = ResourceType.of("taskmigo:role");
    private static final Set<QueryOperator> QUERY_OPERATORS = Set.of(
        QueryOperator.EQ, QueryOperator.NE, QueryOperator.GT, QueryOperator.GE,
        QueryOperator.LT, QueryOperator.LE, QueryOperator.IN
    );
    private static final Set<ObjectAuthorizationOperator> OBJECT_OPERATORS = Set.of(
        ObjectAuthorizationOperator.EQ, ObjectAuthorizationOperator.NE,
        ObjectAuthorizationOperator.GT, ObjectAuthorizationOperator.GE,
        ObjectAuthorizationOperator.LT, ObjectAuthorizationOperator.LE,
        ObjectAuthorizationOperator.IN
    );

    /// Registers the authoritative Role semantic schema.
    @Bean
    ResourceSchema roleResourceSchema() {
        return ResourceSchema.of(TYPE, List.of(
            field("id", LanguageType.Scalar.STRING, false),
            field("code", LanguageType.Scalar.STRING, false),
            field("displayName", LanguageType.Scalar.STRING, false),
            field("description", LanguageType.Scalar.STRING, true)
        ));
    }

    /// Registers Role filtering translation metadata.
    @Bean
    QueryBinding<RoleInfo> roleQueryBinding(@Qualifier("roleResourceSchema") ResourceSchema roleResourceSchema) {
        return new StaticQueryBinding<>(RoleInfo.class, roleResourceSchema, queryFields("id", "code", "displayName", "description"));
    }

    /// Registers Role Object Authorization translation metadata.
    @Bean
    ObjectAuthorizationBinding<RoleInfo> roleObjectAuthorizationBinding(
        @Qualifier("roleResourceSchema") ResourceSchema roleResourceSchema
    ) {
        return new StaticObjectAuthorizationBinding<>(
            RoleInfo.class,
            roleResourceSchema,
            objectFields("id", "code", "displayName", "description")
        );
    }

    @Bean
    QueryPredicateBinder<RoleInfo, RoleEntity> roleQueryPredicateBinder() {
        return new JpaQueryPredicateBinder<>(RoleInfo.class, RoleEntity.class, paths(), types());
    }

    @Bean
    ObjectAuthorizationPredicateBinder<RoleInfo, RoleEntity> roleObjectAuthorizationPredicateBinder() {
        return new JpaObjectAuthorizationPredicateBinder<>(RoleInfo.class, RoleEntity.class, paths(), types());
    }

    private static Field field(String path, LanguageType type, boolean nullable) {
        return new Field(id(path), FieldPath.parse(path), type, nullable);
    }

    private static FieldId id(String path) {
        return FieldId.of("field:" + TYPE.value() + ":" + path);
    }

    private static List<QueryFieldBinding> queryFields(String... paths) {
        return java.util.Arrays.stream(paths)
            .map(path -> new QueryFieldBinding(id(path), QueryPath.parse(path), QUERY_OPERATORS))
            .toList();
    }

    private static List<ObjectAuthorizationFieldBinding> objectFields(String... paths) {
        return java.util.Arrays.stream(paths)
            .map(path -> new ObjectAuthorizationFieldBinding(id(path), path, OBJECT_OPERATORS))
            .toList();
    }

    private static Map<FieldId, String> paths() {
        return Map.of(id("id"), "id", id("code"), "code", id("displayName"), "displayName", id("description"), "description");
    }

    private static Map<FieldId, Class<?>> types() {
        return Map.of(id("id"), java.util.UUID.class, id("code"), String.class, id("displayName"), String.class, id("description"), String.class);
    }
}
