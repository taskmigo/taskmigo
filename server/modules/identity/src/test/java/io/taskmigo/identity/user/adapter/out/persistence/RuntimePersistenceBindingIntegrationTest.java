package io.taskmigo.identity.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationBindingResolver;
import io.taskmigo.authorization.object.ObjectAuthorizationFieldBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationOperator;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.StaticObjectAuthorizationBinding;
import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModels;
import io.taskmigo.identity.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.language.Field;
import io.taskmigo.language.FieldId;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaContext;
import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.QueryBinding;
import io.taskmigo.query.QueryBindingResolver;
import io.taskmigo.query.QueryFieldBinding;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.StaticQueryBinding;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.domain.Specification;

class RuntimePersistenceBindingIntegrationTest {

    private static final ResourceType USER = ResourceType.of("taskmigo:user");
    private static final FieldId RUNTIME_ALIAS = FieldId.of("field:taskmigo:user:runtimeAlias");
    private static final SchemaContext RUNTIME_CONTEXT = new SchemaContext(Map.of("templateId", "runtime"));
    private static final ResourceSchema RUNTIME_SCHEMA = ResourceSchema.of(
        USER,
        List.of(new Field(RUNTIME_ALIAS, FieldPath.parse("runtimeAlias"), LanguageType.Scalar.STRING, false))
    );
    private static final QueryBinding<UserInfo> RUNTIME_QUERY_BINDING = new StaticQueryBinding<>(
        UserInfo.class,
        RUNTIME_SCHEMA,
        List.of(
            new QueryFieldBinding(
                RUNTIME_ALIAS,
                QueryPath.parse("lastName"),
                String.class,
                Set.of(QueryOperator.EQ)
            )
        )
    );
    private static final ObjectAuthorizationBinding<UserInfo> RUNTIME_OBJECT_BINDING =
        new StaticObjectAuthorizationBinding<>(
            UserInfo.class,
            RUNTIME_SCHEMA,
            List.of(
                new ObjectAuthorizationFieldBinding(
                    RUNTIME_ALIAS,
                    "lastName",
                    String.class,
                    Set.of(ObjectAuthorizationOperator.EQ)
                )
            )
        );

    /**
     * Verifies a runtime-selected Query binding survives through the persistence boundary.
     *
     * Given: a runtime schema adds a typed field and maps it to a persistence path absent from the startup binding.
     * Expect: the repository-side binder resolves the exact predicate binding identity and translates the runtime field.
     */
    @Test
    @DisplayName("translates a runtime query binding at the persistence boundary")
    void shouldTranslateRuntimeQueryBindingAtPersistenceBoundary() {
        try (AnnotationConfigApplicationContext context = context()) {
            QueryBindingResolver bindings = context.getBean(QueryBindingResolver.class);
            QueryBindingResolver.Resolution resolution = bindings.resolve(UserInfo.class, RUNTIME_CONTEXT);
            QueryPredicate<UserInfo> predicate = queryPredicate(resolution);
            QueryPredicateBinder<UserInfo, UserEntity> binder = queryBinder(context);

            Specification<UserEntity> specification = binder.bind(predicate);

            assertBindsLastName(specification);
        }
    }

    /**
     * Verifies a runtime-selected Object Authorization binding survives through persistence translation.
     *
     * Given: Object Authorization produced a predicate against a runtime schema fingerprint and runtime field.
     * Expect: the repository-side binder resolves that exact execution binding instead of using the startup binding.
     */
    @Test
    @DisplayName("translates a runtime object authorization binding at the persistence boundary")
    void shouldTranslateRuntimeObjectBindingAtPersistenceBoundary() {
        try (AnnotationConfigApplicationContext context = context()) {
            ObjectAuthorizationPredicate<UserInfo> predicate = ObjectAuthorizationPredicateModels.from(
                RUNTIME_OBJECT_BINDING,
                new ObjectAuthorizationExpression.Binary(
                    ObjectAuthorizationExpression.BinaryOperator.EQUAL,
                    new ObjectAuthorizationExpression.Reference("object", List.of("runtimeAlias"), RUNTIME_ALIAS),
                    new ObjectAuthorizationExpression.Literal("alias")
                )
            );
            ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> binder = objectBinder(context);

            Specification<UserEntity> specification = binder.bind(predicate);

            assertBindsLastName(specification);
        }
    }

    private static AnnotationConfigApplicationContext context() {
        return new AnnotationConfigApplicationContext(UserResourceSchemas.class, RuntimeBindings.class);
    }

    private static QueryPredicate<UserInfo> queryPredicate(QueryBindingResolver.Resolution resolution) {
        return (QueryPredicate<UserInfo>) new FilterByCompiler()
            .compileUntyped(resolution.schema(), resolution.binding(), "object.runtimeAlias == \"alias\"");
    }

    private static QueryPredicateBinder<UserInfo, UserEntity> queryBinder(AnnotationConfigApplicationContext context) {
        return (QueryPredicateBinder<UserInfo, UserEntity>) context.getBean("userQueryPredicateBinder");
    }

    private static ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> objectBinder(
        AnnotationConfigApplicationContext context
    ) {
        return (ObjectAuthorizationPredicateBinder<UserInfo, UserEntity>) context.getBean(
            "userObjectAuthorizationPredicateBinder"
        );
    }

    private static void assertBindsLastName(Specification<UserEntity> specification) {
        Root<UserEntity> root = mock();
        CriteriaQuery<?> query = mock();
        CriteriaBuilder builder = mock();
        Expression<?> lastName = root.get("lastName");
        Predicate expected = mock();
        when(builder.equal(lastName, builder.literal("alias"))).thenReturn(expected);

        assertThat(specification.toPredicate(root, query, builder)).isSameAs(expected);
    }

    @Configuration(proxyBeanMethods = false)
    static class RuntimeBindings {

        @Bean
        QueryBindingResolver queryBindingResolver() {
            return new QueryBindingResolver() {
                @Override
                public QueryBindingResolver.Resolution resolve(Class<?> queryType, SchemaContext context) {
                    if (
                        queryType.equals(UserInfo.class) &&
                        "runtime".equals(context.attributes().get("templateId"))
                    ) {
                        return new QueryBindingResolver.Resolution(RUNTIME_SCHEMA, RUNTIME_QUERY_BINDING);
                    }
                    throw new IllegalStateException("no runtime query binding");
                }

                @Override
                public QueryBinding<?> resolve(Class<?> queryType, String bindingIdentity) {
                    if (queryType.equals(UserInfo.class) && RUNTIME_QUERY_BINDING.identity().equals(bindingIdentity)) {
                        return RUNTIME_QUERY_BINDING;
                    }
                    throw new IllegalStateException("no runtime query binding");
                }
            };
        }

        @Bean
        ObjectAuthorizationBindingResolver objectAuthorizationBindingResolver() {
            return new ObjectAuthorizationBindingResolver() {
                @Override
                public ObjectAuthorizationBinding<?> resolve(Class<?> objectType, SchemaContext context) {
                    if (
                        objectType.equals(UserInfo.class) &&
                        "runtime".equals(context.attributes().get("templateId"))
                    ) {
                        return RUNTIME_OBJECT_BINDING;
                    }
                    throw new IllegalStateException("no runtime object binding");
                }

                @Override
                public ObjectAuthorizationBinding<?> resolve(Class<?> objectType, String bindingIdentity) {
                    if (
                        objectType.equals(UserInfo.class) &&
                        RUNTIME_OBJECT_BINDING.identity().equals(bindingIdentity)
                    ) {
                        return RUNTIME_OBJECT_BINDING;
                    }
                    throw new IllegalStateException("no runtime object binding");
                }
            };
        }
    }
}
