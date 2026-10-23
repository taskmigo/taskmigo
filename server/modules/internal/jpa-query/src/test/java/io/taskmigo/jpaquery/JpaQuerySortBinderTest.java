package io.taskmigo.jpaquery;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySort;
import io.taskmigo.query.TypeDescriptor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JpaQuerySortBinderTest {

    @Test
    @DisplayName("non-null sort fields do not add a redundant null-rank order")
    void shouldOrderNonNullFieldWithoutNullRank() {
        Expression<String> value = mock(Expression.class);
        QueryField<TestEntity, String> field = field("name", false, value);
        QuerySchema<TestEntity> schema = schema(field);
        Root<TestEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Order valueOrder = mock(Order.class);
        doReturn(TestEntity.class).when(query).getResultType();
        when(builder.asc(value)).thenReturn(valueOrder);

        JpaQuerySortBinder.apply(
            query,
            root,
            builder,
            schema,
            QueryFieldContext.empty(),
            List.of(new QuerySort(QueryPath.of("name"), QuerySort.Direction.ASC))
        );

        verify(query).orderBy(List.of(valueOrder));
    }

    @Test
    @DisplayName("nullable sort fields retain nulls-last ordering")
    void shouldOrderNullableFieldWithNullRankFirst() {
        Expression<String> value = mock(Expression.class);
        QueryField<TestEntity, String> field = field("name", true, value);
        QuerySchema<TestEntity> schema = schema(field);
        Root<TestEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        CriteriaBuilder.Case<Integer> missingCase = mock(CriteriaBuilder.Case.class);
        Predicate isNull = mock(Predicate.class);
        Expression<Integer> missing = mock(Expression.class);
        Order missingOrder = mock(Order.class);
        Order valueOrder = mock(Order.class);
        doReturn(TestEntity.class).when(query).getResultType();
        when(builder.<Integer>selectCase()).thenReturn(missingCase);
        when(builder.isNull(value)).thenReturn(isNull);
        when(missingCase.when(isNull, 1)).thenReturn(missingCase);
        when(missingCase.otherwise(0)).thenReturn(missing);
        when(builder.asc(missing)).thenReturn(missingOrder);
        when(builder.asc(value)).thenReturn(valueOrder);

        JpaQuerySortBinder.apply(
            query,
            root,
            builder,
            schema,
            QueryFieldContext.empty(),
            List.of(new QuerySort(QueryPath.of("name"), QuerySort.Direction.ASC))
        );

        verify(query).orderBy(List.of(missingOrder, valueOrder));
    }

    private static QueryField<TestEntity, String> field(String name, boolean nullable, Expression<String> value) {
        JpaQueryValueExpression<TestEntity, String> expression = new JpaQueryValueExpression<>() {
            @Override
            public Expression<String> resolve(Root<TestEntity> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
                return value;
            }

            @Override
            public Class<String> javaType() {
                return String.class;
            }
        };
        return QueryField.dynamic(
            QueryPath.of(name),
            TypeDescriptor.of(String.class),
            nullable,
            String.class,
            expression
        ).sortable();
    }

    private static QuerySchema<TestEntity> schema(QueryField<TestEntity, ?> field) {
        return new QuerySchema<>(TestEntity.class) {
            @Override
            protected String operationId() {
                return "test.sort";
            }

            @Override
            protected Collection<QueryField<TestEntity, ?>> staticFields() {
                return List.of(field);
            }

            @Override
            protected Collection<QueryField<TestEntity, ?>> runtimeFields(QueryFieldContext context) {
                return List.of();
            }
        };
    }

    private static final class TestEntity {}
}
