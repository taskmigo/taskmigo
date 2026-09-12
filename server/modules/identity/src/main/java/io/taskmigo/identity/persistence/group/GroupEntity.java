package io.taskmigo.identity.persistence.group;

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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "groups")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
public class GroupEntity {

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

    @ManyToMany(mappedBy = "childGroups")
    Set<GroupEntity> parentGroups = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "group_roles", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "role_id", nullable = false)
    Set<UUID> roleIds = new LinkedHashSet<>();

    protected GroupEntity() {}

    public GroupEntity(UUID id, String name, @Nullable String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public UUID id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public @Nullable String description() {
        return this.description;
    }

    public Set<UUID> memberIds() {
        return Set.copyOf(this.memberIds);
    }

    public Set<UUID> roleIds() {
        return Set.copyOf(this.roleIds);
    }

    public Set<GroupEntity> childGroups() {
        return Set.copyOf(this.childGroups);
    }

    public void addChildGroups(Collection<GroupEntity> children) {
        this.childGroups.addAll(children);
    }

    public void replaceChildGroups(Collection<GroupEntity> children) {
        this.childGroups.clear();
        this.childGroups.addAll(children);
    }

    public void addMember(UUID userId) {
        this.memberIds.add(userId);
    }

    public void replaceRoleIds(Set<UUID> roleIds) {
        this.roleIds.clear();
        this.roleIds.addAll(roleIds);
    }
}
