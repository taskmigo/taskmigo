package io.taskmigo.auth;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "groups")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class GroupEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 200)
    String name;

    @Column(length = 1000)
    @Nullable
    String description;

    @ElementCollection
    @CollectionTable(name = "group_members", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "user_id", nullable = false)
    Set<UUID> memberIds = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
        name = "group_hierarchy",
        joinColumns = @JoinColumn(name = "parent_group_id"),
        inverseJoinColumns = @JoinColumn(name = "child_group_id")
    )
    @OrderBy("id")
    Set<GroupEntity> childGroups = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "group_roles", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "role_id", nullable = false)
    Set<UUID> roleIds = new LinkedHashSet<>();

    protected GroupEntity() {}

    GroupEntity(UUID id, String name, @Nullable String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }
}
