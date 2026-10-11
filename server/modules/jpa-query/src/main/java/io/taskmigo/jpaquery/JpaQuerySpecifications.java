package io.taskmigo.jpaquery;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/// Composes system-owned mandatory authorization with an optional untrusted client filter.
public final class JpaQuerySpecifications {

    private JpaQuerySpecifications() {}

    /// Returns mandatory authorization alone or authorization AND the optional client filter.
    ///
    /// Client filter structure remains nested below this system-owned top-level AND, so client OR/NOT/parentheses
    /// cannot widen the authorized object space.
    public static <E> Specification<E> authorized(
        Specification<E> authorization,
        @Nullable Specification<E> clientFilter
    ) {
        Objects.requireNonNull(authorization, "authorization");
        return clientFilter == null ? authorization : authorization.and(clientFilter);
    }
}
