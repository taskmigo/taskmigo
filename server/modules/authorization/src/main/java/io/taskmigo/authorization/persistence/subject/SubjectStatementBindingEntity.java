package io.taskmigo.authorization.persistence.subject;

import io.taskmigo.authorization.subject.SubjectRef;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
    name = "subject_statement_bindings",
    uniqueConstraints = @UniqueConstraint(columnNames = { "subject_type", "subject_id", "statement_id" })
)
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
public class SubjectStatementBindingEntity {

    @Id
    UUID id;

    @Column(name = "subject_type", nullable = false, length = 100)
    String subjectType;

    @Column(name = "subject_id", nullable = false)
    UUID subjectId;

    @Column(name = "statement_id", nullable = false)
    UUID statementId;

    protected SubjectStatementBindingEntity() {}

    public SubjectStatementBindingEntity(UUID id, SubjectRef subject, UUID statementId) {
        this.id = id;
        this.subjectType = subject.type();
        this.subjectId = subject.id();
        this.statementId = statementId;
    }

    public UUID statementId() {
        return this.statementId;
    }
}
