package io.taskmigo.group;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "groups")
@SuppressWarnings("NotNullFieldNotInitialized")
class GroupEntity {

    @Id
    UUID id;

    @Column(name = "organization_id", nullable = false)
    UUID organizationId;

    @Column(nullable = false, length = 200)
    String name;

    @Column(length = 1000)
    @Nullable
    String description;

    @ElementCollection
    @CollectionTable(name = "group_members", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "user_id", nullable = false)
    Set<UUID> memberIds = new LinkedHashSet<>();

    protected GroupEntity() {}

    GroupEntity(UUID id, UUID organizationId, String name, @Nullable String description) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.description = description;
    }
}
