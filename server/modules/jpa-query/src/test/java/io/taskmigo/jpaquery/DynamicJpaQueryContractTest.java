package io.taskmigo.jpaquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySort;
import io.taskmigo.query.TypeDescriptor;
import io.taskmigo.query.model.QueryExpression;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.jpa.domain.Specification;

class DynamicJpaQueryContractTest {

    @Test
    @DisplayName("dynamic QueryField exposes runtime metadata without pretending to be a static JpaPath")
    void shouldExposeDynamicQueryFieldMetadata() {
        JpaQueryValueExpression<TestEntity, String> expression = new JpaQueryValueExpression<>() {
            @Override
            public Expression<String> resolve(Root<TestEntity> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
                return mock(Expression.class);
            }

            @Override
            public Class<String> javaType() {
                return String.class;
            }
        };

        QueryField<TestEntity, String> field = QueryField.dynamic(
            QueryPath.of("severity"),
            TypeDescriptor.of(String.class),
            true,
            String.class,
            expression
        )
            .operators(QueryOperator.EQ, QueryOperator.NE, QueryOperator.CONTAINS)
            .sortable();

        assertThat(field.descriptor().path()).isEqualTo(QueryPath.of("severity"));
        assertThat(field.descriptor().operators()).isEqualTo(
            Set.of(QueryOperator.EQ, QueryOperator.NE, QueryOperator.CONTAINS)
        );
        assertThat(field.descriptor().sortable()).isTrue();
        assertThat(field.javaType()).isEqualTo(String.class);
    }

    @Test
    @DisplayName("expression binder resolves dynamic fields from the supplied runtime context")
    void shouldBindDynamicExpressionWithRuntimeContext() {
        Expression<String> dynamicValue = mock(Expression.class);
        Predicate predicate = mock(Predicate.class);
        JpaQueryValueExpression<TestEntity, String> expression = new JpaQueryValueExpression<>() {
            @Override
            public Expression<String> resolve(Root<TestEntity> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
                return dynamicValue;
            }

            @Override
            public Class<String> javaType() {
                return String.class;
            }
        };
        QueryField<TestEntity, String> dynamic = QueryField.dynamic(
            QueryPath.of("severity"),
            TypeDescriptor.of(String.class),
            true,
            String.class,
            expression
        ).operators(QueryOperator.EQ);
        QuerySchema<TestEntity> schema = new QuerySchema<>(TestEntity.class) {
            @Override
            protected String operationId() {
                return "test.dynamic";
            }

            @Override
            protected Collection<QueryField<TestEntity, ?>> staticFields() {
                return List.of();
            }

            @Override
            protected Collection<QueryField<TestEntity, ?>> runtimeFields(QueryFieldContext context) {
                return "incident".equals(context.values().get("scope")) ? List.of(dynamic) : List.of();
            }
        };
        QueryFieldContext context = new QueryFieldContext(Map.of("scope", "incident"));
        QueryExpression comparison = new QueryExpression.Binary(
            QueryExpression.BinaryOperator.EQUAL,
            new QueryExpression.Reference("object", List.of("severity")),
            new QueryExpression.Literal("HIGH")
        );
        Specification<TestEntity> specification = JpaQueryExpressionBinder.bind(comparison, schema, context);
        Root<TestEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Expression<String> literal = mock(Expression.class);
        when(builder.literal("HIGH")).thenReturn(literal);
        when(builder.equal(dynamicValue, literal)).thenReturn(predicate);

        Predicate bound = specification.toPredicate(root, query, builder);

        assertThat(bound).isSameAs(predicate);
        verify(builder).equal(dynamicValue, literal);
    }

    @Test
    @DisplayName("sort binder applies missing-last ordering and a stable tie breaker but skips count queries")
    void shouldApplyStableSortingAndSkipCountQueries() {
        JpaQueryValueExpression<TestEntity, String> expression = new JpaQueryValueExpression<>() {
            @Override
            public Expression<String> resolve(Root<TestEntity> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
                return mock(Expression.class);
            }

            @Override
            public Class<String> javaType() {
                return String.class;
            }
        };
        QueryField<TestEntity, String> dynamic = QueryField.dynamic(
            QueryPath.of("severity"),
            TypeDescriptor.of(String.class),
            true,
            String.class,
            expression
        ).sortable();
        QuerySchema<TestEntity> schema = new QuerySchema<>(TestEntity.class) {
            @Override
            protected String operationId() {
                return "test.sort";
            }

            @Override
            protected Collection<QueryField<TestEntity, ?>> staticFields() {
                return List.of();
            }

            @Override
            protected Collection<QueryField<TestEntity, ?>> runtimeFields(QueryFieldContext context) {
                return List.of(dynamic);
            }
        };
        Root<TestEntity> root = mock(Root.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        JpaPath<TestEntity, ?> tieBreaker = mock(JpaPath.class);
        CriteriaQuery<?> countQuery = mock(CriteriaQuery.class);
        Mockito.doReturn(Long.class).when(countQuery).getResultType();

        JpaQuerySortBinder.apply(
            countQuery,
            root,
            builder,
            schema,
            QueryFieldContext.empty(),
            List.of(new QuerySort(QueryPath.of("severity"), QuerySort.Direction.ASC)),
            tieBreaker
        );

        verify(countQuery, Mockito.never()).orderBy(ArgumentMatchers.<Order>anyList());
    }

    private static final class TestEntity {}
}
