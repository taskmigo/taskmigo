package io.taskmigo.query;

import java.util.Collection;
import java.util.Optional;

/// Temporary migration carrier for existing application declarations; it is removed after binding migration.
public interface QuerySchema<Q> {
    Class<Q> queryType();

    Optional<QueryField> field(QueryPath path);

    Collection<QueryField> fields();
}
