package io.taskmigo.authorization.role.infrastructure.persistence;

import io.taskmigo.authorization.role.domain.Role;
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
@Table(name = "roles")
@SuppressWarnings("NotNullFieldNotInitialized")
public class RoleEntity {

    @Id
    UUID id;

    @Column(nullable = false, unique = true)
    String code;

    @Column(name = "display_name", nullable = false)
    String displayName;

    @Column(length = 1000)
    @Nullable
    String description;

    @ElementCollection
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

    private RoleEntity(Role role) {
        this.id = role.id();
        this.code = role.code().value();
        this.displayName = role.profile().displayName();
        this.description = role.profile().description();
        this.statementIds.addAll(role.statementIds());
    }

    static RoleEntity from(Role role) {
        return new RoleEntity(role);
    }

    Role toDomain() {
        return Role.restore(this.id, this.code, this.displayName, this.description, this.statementIds);
    }

    void update(Role role) {
        this.displayName = role.profile().displayName();
        this.description = role.profile().description();
        this.statementIds.clear();
        this.statementIds.addAll(role.statementIds());
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

    /// Returns direct Statement ids for the optimized effective-authorization read path.
    public Set<UUID> statementIds() {
        return Set.copyOf(this.statementIds);
    }

    Set<RoleEntity> childRoles() {
        return Set.copyOf(this.childRoles);
    }

    void replaceChildRoles(Collection<RoleEntity> children) {
        this.childRoles.clear();
        this.childRoles.addAll(children);
    }
}
