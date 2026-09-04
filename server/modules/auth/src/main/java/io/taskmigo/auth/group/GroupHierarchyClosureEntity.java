package io.taskmigo.auth.group;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/// Stores one reflexive-transitive Group hierarchy relationship for bounded authorization resolution.
@Entity
@Table(name = "group_hierarchy_closure")
public class GroupHierarchyClosureEntity {

    @EmbeddedId
    GroupHierarchyClosureId id;

    protected GroupHierarchyClosureEntity() {}

    GroupHierarchyClosureEntity(UUID ancestorGroupId, UUID descendantGroupId) {
        this.id = new GroupHierarchyClosureId(ancestorGroupId, descendantGroupId);
    }

    /// Returns the Group from which the relationship is resolved.
    public UUID ancestorGroupId() {
        return this.id.ancestorGroupId;
    }

    /// Returns the Group reachable from the ancestor, including the ancestor itself.
    public UUID descendantGroupId() {
        return this.id.descendantGroupId;
    }

    /// Identifies one ancestor-to-descendant Group relationship.
    @Embeddable
    public static class GroupHierarchyClosureId {

        @Column(name = "ancestor_group_id")
        UUID ancestorGroupId;

        @Column(name = "descendant_group_id")
        UUID descendantGroupId;

        protected GroupHierarchyClosureId() {}

        GroupHierarchyClosureId(UUID ancestorGroupId, UUID descendantGroupId) {
            this.ancestorGroupId = ancestorGroupId;
            this.descendantGroupId = descendantGroupId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof GroupHierarchyClosureId value)) return false;
            return (
                this.ancestorGroupId.equals(value.ancestorGroupId) &&
                this.descendantGroupId.equals(value.descendantGroupId)
            );
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(this.ancestorGroupId, this.descendantGroupId);
        }
    }
}
