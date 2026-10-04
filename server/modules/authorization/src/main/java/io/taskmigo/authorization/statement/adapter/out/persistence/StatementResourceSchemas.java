package io.taskmigo.authorization.statement.adapter.out.persistence;

import io.taskmigo.authorization.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationFieldBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationOperator;
import io.taskmigo.authorization.object.StaticObjectAuthorizationBinding;
import io.taskmigo.authorization.statement.StatementInfo;
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

/// Registers Statement semantic fields and their query and Object Authorization execution bindings.
@Configuration(proxyBeanMethods = false)
public class StatementResourceSchemas {

    private static final ResourceType TYPE = ResourceType.of("taskmigo:statement");
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

    /// Registers the authoritative Statement semantic schema.
    @Bean
    ResourceSchema statementResourceSchema() {
        return ResourceSchema.of(TYPE, List.of(
            field("id", LanguageType.Scalar.STRING, false),
            field("code", LanguageType.Scalar.STRING, false),
            field("description", LanguageType.Scalar.STRING, true),
            field("target.api.method", LanguageType.Scalar.STRING, false),
            field("target.api.path", LanguageType.Scalar.STRING, false)
        ));
    }

    /// Registers Statement filtering translation metadata.
    @Bean
    QueryBinding<StatementInfo> statementQueryBinding(@Qualifier("statementResourceSchema") ResourceSchema statementResourceSchema) {
        return new StaticQueryBinding<>(
            StatementInfo.class,
            statementResourceSchema,
            queryFields("id", "code", "description", "target.api.method", "target.api.path")
        );
    }

    /// Registers Statement Object Authorization translation metadata.
    @Bean
    ObjectAuthorizationBinding<StatementInfo> statementObjectAuthorizationBinding(
        @Qualifier("statementResourceSchema") ResourceSchema statementResourceSchema
    ) {
        return new StaticObjectAuthorizationBinding<>(
            StatementInfo.class,
            statementResourceSchema,
            objectFields("id", "code", "description", "target.api.method", "target.api.path")
        );
    }

    @Bean
    QueryPredicateBinder<StatementInfo, StatementEntity> statementQueryPredicateBinder(
        @Qualifier("statementQueryBinding") QueryBinding<StatementInfo> binding
    ) {
        return new JpaQueryPredicateBinder<>(StatementEntity.class, binding, types());
    }

    @Bean
    ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> statementObjectAuthorizationPredicateBinder(
        @Qualifier("statementObjectAuthorizationBinding") ObjectAuthorizationBinding<StatementInfo> binding
    ) {
        return new JpaObjectAuthorizationPredicateBinder<>(StatementEntity.class, binding, types());
    }

    private static Field field(String path, LanguageType type, boolean nullable) {
        return new Field(id(path), FieldPath.parse(path), type, nullable);
    }

    private static FieldId id(String path) {
        return FieldId.of("field:" + TYPE.value() + ":" + path);
    }

    private static List<QueryFieldBinding> queryFields(String... paths) {
        return Arrays.stream(paths)
            .map(path -> new QueryFieldBinding(id(path), QueryPath.parse(physicalPath(path)), QUERY_OPERATORS))
            .toList();
    }

    private static List<ObjectAuthorizationFieldBinding> objectFields(String... paths) {
        return Arrays.stream(paths)
            .map(path -> new ObjectAuthorizationFieldBinding(id(path), physicalPath(path), OBJECT_OPERATORS))
            .toList();
    }

    private static Map<FieldId, Class<?>> types() {
        return Map.of(
            id("id"), UUID.class, id("code"), String.class, id("description"), String.class,
            id("target.api.method"), String.class, id("target.api.path"), String.class
        );
    }

    private static String physicalPath(String semanticPath) {
        return switch (semanticPath) {
            case "target.api.method" -> "method";
            case "target.api.path" -> "path";
            default -> semanticPath;
        };
    }
}
