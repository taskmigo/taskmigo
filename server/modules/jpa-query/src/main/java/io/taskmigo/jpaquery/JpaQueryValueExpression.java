package io.taskmigo.jpaquery;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

/// Resolves one typed database-side value expression for a Query field.
public interface JpaQueryValueExpression<E, V> {
    @SuppressWarnings("NullableProblems")
    Expression<V> resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder builder);

    Class<V> javaType();
}
