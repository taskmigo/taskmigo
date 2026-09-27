package io.taskmigo.audit.adapter.out.persistence;

import io.taskmigo.audit.AuditChange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "audit_log_changes")
class AuditChangeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_log_id", nullable = false)
    private AuditLogEntity log;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "field_name", nullable = false)
    private String field;

    @Column(name = "sensitive", nullable = false)
    private boolean sensitive;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value", columnDefinition = "jsonb")
    private @Nullable Object beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value", columnDefinition = "jsonb")
    private @Nullable Object afterValue;

    protected AuditChangeEntity() {}

    AuditChangeEntity(AuditLogEntity log, int position, AuditChange change) {
        this.id = UUID.randomUUID();
        this.log = log;
        this.position = position;
        this.field = change.field();
        this.sensitive = change.sensitive();
        this.beforeValue = change.beforeValue();
        this.afterValue = change.afterValue();
    }

    AuditChange toModel() {
        return new AuditChange(this.field, this.sensitive, this.beforeValue, this.afterValue);
    }
}
