package io.taskmigo.identity.user.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/// Maps session ownership only; database cascade removes serialized attributes without loading them.
@Entity
@Table(name = "spring_session")
@SuppressWarnings("NotNullFieldNotInitialized")
class HttpSessionEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "primary_id", length = 36)
    String id;

    @Column(name = "principal_name", length = 100)
    @Nullable
    String principalName;

    protected HttpSessionEntity() {}
}
