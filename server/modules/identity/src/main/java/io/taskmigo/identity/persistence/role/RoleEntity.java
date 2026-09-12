package io.taskmigo.identity.persistence.role;

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
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
public class RoleEntity {

    @Id
    UUID id;

    @Column(nullable = false, unique = true)
    String name;

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

    public RoleEntity(UUID id, String name, @Nullable String description) {
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

    public void updateDescription(@Nullable String description) {
        this.description = description;
    }
}
