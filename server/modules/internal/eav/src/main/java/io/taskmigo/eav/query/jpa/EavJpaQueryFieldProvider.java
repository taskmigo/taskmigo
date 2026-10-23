package io.taskmigo.eav.query.jpa;

import io.taskmigo.eav.query.EavQueryField;
import io.taskmigo.jpaquery.JpaPath;
import io.taskmigo.jpaquery.QueryField;
import java.util.UUID;

/// Creates JPA query bindings for resolved generic EAV fields without exposing EAV persistence internals.
public interface EavJpaQueryFieldProvider {
    /// Binds one resolved EAV field to a consumer entity's schema-instance path.
    <E> QueryField<E, ?> create(EavQueryField field, JpaPath<E, UUID> schemaInstancePath);
}
