package io.taskmigo.auth.role;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/// Stores one reflexive-transitive Role hierarchy relationship for bounded authorization resolution.
@Entity
@Table(name = "role_hierarchy_closure")
@SuppressWarnings("NotNullFieldNotInitialized")
public class RoleHierarchyClosureEntity {

    @EmbeddedId
    RoleHierarchyClosureId id;

    protected RoleHierarchyClosureEntity() {}

    RoleHierarchyClosureEntity(UUID ancestorRoleId, UUID descendantRoleId) {
        this.id = new RoleHierarchyClosureId(ancestorRoleId, descendantRoleId);
    }

    /// Returns the Role from which the relationship is resolved.
    public UUID ancestorRoleId() {
        return this.id.ancestorRoleId;
    }

    /// Returns the Role reachable from the ancestor, including the ancestor itself.
    public UUID descendantRoleId() {
        return this.id.descendantRoleId;
    }

    /// Identifies one ancestor-to-descendant Role relationship.
    @Embeddable
    public static class RoleHierarchyClosureId {

        @Column(name = "ancestor_role_id")
        UUID ancestorRoleId;

        @Column(name = "descendant_role_id")
        UUID descendantRoleId;

        protected RoleHierarchyClosureId() {}

        RoleHierarchyClosureId(UUID ancestorRoleId, UUID descendantRoleId) {
            this.ancestorRoleId = ancestorRoleId;
            this.descendantRoleId = descendantRoleId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RoleHierarchyClosureId value)) {
                return false;
            }
            return (
                this.ancestorRoleId.equals(value.ancestorRoleId) && this.descendantRoleId.equals(value.descendantRoleId)
            );
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(this.ancestorRoleId, this.descendantRoleId);
        }
    }
}
