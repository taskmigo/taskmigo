package io.taskmigo.identity.persistence.group;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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
@SuppressWarnings("NotNullFieldNotInitialized")
public class GroupEntity {

    @Id
    UUID id;

    @Column(nullable = false, unique = true, length = 200)
    String code;

    @Column(name = "display_name", nullable = false, length = 200)
    String displayName;

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

    protected GroupEntity() {}

    public GroupEntity(UUID id, String code, String displayName, @Nullable String description) {
        this.id = id;
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public UUID id() {
        return this.id;
    }

    public String code() {
        return this.code;
    }

    public String displayName() {
        return this.displayName;
    }

    public @Nullable String description() {
        return this.description;
    }

    public Set<UUID> memberIds() {
        return Set.copyOf(this.memberIds);
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

    public void replaceMembers(Set<UUID> memberIds) {
        this.memberIds.clear();
        this.memberIds.addAll(memberIds);
    }

    public void updateDisplayNameAndDescription(String displayName, @Nullable String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
