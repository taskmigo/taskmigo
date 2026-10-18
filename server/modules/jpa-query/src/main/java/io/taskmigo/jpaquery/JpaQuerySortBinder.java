package io.taskmigo.jpaquery;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QuerySort;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Applies approved Query sorting to one Criteria query with deterministic missing-last semantics.
public final class JpaQuerySortBinder {

    private JpaQuerySortBinder() {}

    public static <E> void apply(
        CriteriaQuery<?> query,
        Root<E> root,
        CriteriaBuilder builder,
        QuerySchema<E> schema,
        QueryFieldContext context,
        List<QuerySort> sorting,
        JpaPath<E, ?> stableTieBreaker
    ) {
        Objects.requireNonNull(query);
        Objects.requireNonNull(root);
        Objects.requireNonNull(builder);
        Objects.requireNonNull(schema);
        Objects.requireNonNull(context);
        List<QuerySort> requested = List.copyOf(Objects.requireNonNull(sorting));
        Objects.requireNonNull(stableTieBreaker);
        Class<?> resultType = query.getResultType();
        if (resultType == Long.class || resultType == long.class || requested.isEmpty()) {
            return;
        }

        ArrayList<Order> orders = new ArrayList<>(requested.size() * 2 + 1);
        for (QuerySort sort : requested) {
            QueryField<E, ?> field = schema
                .binding(sort.path(), context)
                .orElseThrow(() ->
                    new IllegalArgumentException("Persistence sort path is not bound: " + sort.path().text())
                );
            if (!field.descriptor().sortable()) {
                throw new IllegalArgumentException("Query field is not sortable: " + sort.path().text());
            }
            Expression<?> value = field.valueExpression().resolve(root, query, builder);
            Expression<Integer> missing = builder.<Integer>selectCase().when(builder.isNull(value), 1).otherwise(0);
            orders.add(builder.asc(missing));
            orders.add(sort.direction() == QuerySort.Direction.ASC ? builder.asc(value) : builder.desc(value));
        }
        orders.add(builder.asc(stableTieBreaker.resolve(root)));
        query.orderBy(List.copyOf(orders));
    }
}
