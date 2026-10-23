package io.taskmigo.jpaquery;

import io.taskmigo.query.ListQuerySpec;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;

/// Bridges ordered [ListQuerySpec] metadata into Spring Data JPA specifications.
public final class JpaQuerySortSpecifications {

    private JpaQuerySortSpecifications() {}

    /// Returns a specification that applies the client-requested ordering from the supplied query.
    public static <Q, E> Specification<E> bind(ListQuerySpec<Q> query, QuerySchema<E> schema) {
        Objects.requireNonNull(query);
        Objects.requireNonNull(schema);
        return (root, criteria, builder) -> {
            JpaQuerySortBinder.apply(
                Objects.requireNonNull(criteria),
                root,
                builder,
                schema,
                query.context(),
                query.sorting()
            );
            return builder.conjunction();
        };
    }

    /// Returns whether the query carries at least one explicit client sort clause.
    public static boolean hasSorting(ListQuerySpec<?> query) {
        Objects.requireNonNull(query);
        return !query.sorting().isEmpty();
    }
}
