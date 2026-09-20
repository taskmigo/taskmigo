package io.taskmigo.identity.membership.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Stores one direct User-to-Group membership.
@Entity
@Table(name = "group_members")
@SuppressWarnings("NotNullFieldNotInitialized")
class GroupMembershipEntity {

    @EmbeddedId
    GroupMembershipId id;

    protected GroupMembershipEntity() {}

    GroupMembershipEntity(UUID groupId, UUID userId) {
        this.id = new GroupMembershipId(groupId, userId);
    }

    GroupMembershipId id() {
        return this.id;
    }

    /// Identifies one direct membership without loading either aggregate.
    @Embeddable
    static class GroupMembershipId implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Column(name = "group_id")
        UUID groupId;

        @Column(name = "user_id")
        UUID userId;

        protected GroupMembershipId() {}

        GroupMembershipId(UUID groupId, UUID userId) {
            this.groupId = groupId;
            this.userId = userId;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GroupMembershipId value)) {
                return false;
            }
            return this.groupId.equals(value.groupId) && this.userId.equals(value.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId, this.userId);
        }
    }
}
