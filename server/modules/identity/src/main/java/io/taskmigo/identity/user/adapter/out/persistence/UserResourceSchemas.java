package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationFieldBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationOperator;
import io.taskmigo.authorization.object.StaticObjectAuthorizationBinding;
import io.taskmigo.identity.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserStatus;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Registers User semantic fields and their query and Object Authorization execution bindings.
@Configuration(proxyBeanMethods = false)
public class UserResourceSchemas {

    private static final ResourceType TYPE = ResourceType.of("taskmigo:user");
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

    /// Registers the authoritative User semantic schema.
    @Bean
    ResourceSchema userResourceSchema() {
        return ResourceSchema.of(TYPE, List.of(
            field("id", LanguageType.Scalar.STRING, false),
            field("username", LanguageType.Scalar.STRING, false),
            field("firstName", LanguageType.Scalar.STRING, false),
            field("lastName", LanguageType.Scalar.STRING, false),
            field("status", LanguageType.Scalar.STRING, false),
            field("retainedAt", LanguageType.Scalar.STRING, true)
        ));
    }

    /// Registers User filtering translation metadata.
    @Bean
    QueryBinding<UserInfo> userQueryBinding(@Qualifier("userResourceSchema") ResourceSchema userResourceSchema) {
        return new StaticQueryBinding<>(UserInfo.class, userResourceSchema, queryFields("id", "username", "firstName", "lastName"));
    }

    /// Registers User Object Authorization translation metadata.
    @Bean
    ObjectAuthorizationBinding<UserInfo> userObjectAuthorizationBinding(
        @Qualifier("userResourceSchema") ResourceSchema userResourceSchema
    ) {
        return new StaticObjectAuthorizationBinding<>(
            UserInfo.class,
            userResourceSchema,
            objectFields("id", "username", "firstName", "lastName", "status", "retainedAt")
        );
    }

    @Bean
    QueryPredicateBinder<UserInfo, UserEntity> userQueryPredicateBinder() {
        return new JpaQueryPredicateBinder<>(UserInfo.class, UserEntity.class, queryPaths(), queryTypes());
    }

    @Bean
    ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> userObjectAuthorizationPredicateBinder() {
        return new JpaObjectAuthorizationPredicateBinder<>(UserInfo.class, UserEntity.class, objectPaths(), objectTypes());
    }

    private static Field field(String path, LanguageType type, boolean nullable) {
        return new Field(id(path), FieldPath.parse(path), type, nullable);
    }

    private static FieldId id(String path) {
        return FieldId.of("field:" + TYPE.value() + ":" + path);
    }

    private static List<QueryFieldBinding> queryFields(String... paths) {
        return Arrays.stream(paths)
            .map(path -> new QueryFieldBinding(id(path), QueryPath.parse(path), QUERY_OPERATORS))
            .toList();
    }

    private static List<ObjectAuthorizationFieldBinding> objectFields(String... paths) {
        return Arrays.stream(paths)
            .map(path -> new ObjectAuthorizationFieldBinding(id(path), path, OBJECT_OPERATORS))
            .toList();
    }

    private static Map<FieldId, String> queryPaths() {
        return Map.of(id("id"), "id", id("username"), "username", id("firstName"), "firstName", id("lastName"), "lastName");
    }

    private static Map<FieldId, Class<?>> queryTypes() {
        return Map.of(id("id"), UUID.class, id("username"), String.class, id("firstName"), String.class, id("lastName"), String.class);
    }

    private static Map<FieldId, String> objectPaths() {
        return Map.of(
            id("id"), "id", id("username"), "username", id("firstName"), "firstName",
            id("lastName"), "lastName", id("status"), "status", id("retainedAt"), "retainedAt"
        );
    }

    private static Map<FieldId, Class<?>> objectTypes() {
        return Map.of(
            id("id"), UUID.class, id("username"), String.class, id("firstName"), String.class,
            id("lastName"), String.class, id("status"), UserStatus.class, id("retainedAt"), String.class
        );
    }
}
