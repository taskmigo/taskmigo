package io.taskmigo.identity.group.adapter.out.persistence;

import io.taskmigo.identity.group.domain.Group;
import jakarta.persistence.Column;
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

    private GroupEntity(UUID id, String code, String displayName, @Nullable String description) {
        this.id = id;
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    static GroupEntity from(Group group) {
        return new GroupEntity(
            group.id(),
            group.code().value(),
            group.profile().displayName(),
            group.profile().description()
        );
    }

    Group toDomain() {
        return Group.restore(this.id, this.code, this.displayName, this.description);
    }

    UUID id() {
        return this.id;
    }

    String code() {
        return this.code;
    }

    String displayName() {
        return this.displayName;
    }

    @Nullable
    String description() {
        return this.description;
    }

    Set<GroupEntity> childGroups() {
        return Set.copyOf(this.childGroups);
    }

    void replaceChildGroups(Collection<GroupEntity> children) {
        this.childGroups.clear();
        this.childGroups.addAll(children);
    }

    void updateProfile(String displayName, @Nullable String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
