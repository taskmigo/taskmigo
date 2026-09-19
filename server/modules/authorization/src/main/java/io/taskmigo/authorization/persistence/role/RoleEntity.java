package io.taskmigo.authorization.persistence.role;

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
@Table(name = "roles")
@SuppressWarnings("NotNullFieldNotInitialized")
public class RoleEntity {

    @Id
    UUID id;

    @Column(nullable = false, unique = true)
    String code;

    @Column(name = "display_name", nullable = false, length = 255)
    String displayName;

    @Column(length = 1000)
    @Nullable
    String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_statements", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "statement_id", nullable = false)
    Set<UUID> statementIds = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
        name = "role_hierarchy",
        joinColumns = @JoinColumn(name = "parent_role_id"),
        inverseJoinColumns = @JoinColumn(name = "child_role_id")
    )
    @OrderBy("id")
    Set<RoleEntity> childRoles = new LinkedHashSet<>();

    @ManyToMany(mappedBy = "childRoles")
    Set<RoleEntity> parentRoles = new LinkedHashSet<>();

    protected RoleEntity() {}

    public RoleEntity(UUID id, String code, String displayName, @Nullable String description) {
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

    public Set<UUID> statementIds() {
        return Set.copyOf(this.statementIds);
    }

    public Set<RoleEntity> childRoles() {
        return Set.copyOf(this.childRoles);
    }

    public void addChildRoles(Collection<RoleEntity> children) {
        this.childRoles.addAll(children);
    }

    public void replaceChildRoles(Collection<RoleEntity> children) {
        this.childRoles.clear();
        this.childRoles.addAll(children);
    }

    public void replaceStatementIds(Set<UUID> statementIds) {
        this.statementIds.clear();
        this.statementIds.addAll(statementIds);
    }

    public void updateDisplayNameAndDescription(String displayName, @Nullable String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
