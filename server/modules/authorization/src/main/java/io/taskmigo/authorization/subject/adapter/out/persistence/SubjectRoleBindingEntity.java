package io.taskmigo.authorization.subject.adapter.out.persistence;

import io.taskmigo.authorization.subject.SubjectRef;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
    name = "subject_role_bindings",
    uniqueConstraints = @UniqueConstraint(columnNames = { "subject_type", "subject_id", "role_id" })
)
@SuppressWarnings("NotNullFieldNotInitialized")
public class SubjectRoleBindingEntity {

    @Id
    UUID id;

    @Column(name = "subject_type", nullable = false, length = 100)
    String subjectType;

    @Column(name = "subject_id", nullable = false)
    UUID subjectId;

    @Column(name = "role_id", nullable = false)
    UUID roleId;

    protected SubjectRoleBindingEntity() {}

    public SubjectRoleBindingEntity(UUID id, SubjectRef subject, UUID roleId) {
        this.id = id;
        this.subjectType = subject.type();
        this.subjectId = subject.id();
        this.roleId = roleId;
    }

    public UUID roleId() {
        return this.roleId;
    }
}
