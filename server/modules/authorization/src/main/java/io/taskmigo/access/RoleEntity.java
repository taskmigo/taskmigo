package io.taskmigo.access;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "roles")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class RoleEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 16)
    String origin;

    @Nullable
    @Column(name = "organization_id")
    UUID organizationId;

    @Column(name = "role_key", nullable = false, length = 100)
    String key;

    @Column(nullable = false, length = 200)
    String name;

    @Nullable
    @Column(length = 1000)
    String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_statements", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "statement_id", nullable = false)
    Set<UUID> statementIds = new LinkedHashSet<>();

    protected RoleEntity() {}

    RoleEntity(
        UUID id,
        String origin,
        @Nullable UUID organizationId,
        String key,
        String name,
        @Nullable String description,
        Set<UUID> statementIds
    ) {
        this.id = id;
        this.origin = origin;
        this.organizationId = organizationId;
        this.key = key;
        this.name = name;
        this.description = description;
        this.statementIds.addAll(statementIds);
    }

    void replace(String name, @Nullable String description, Set<UUID> statementIds) {
        this.name = name;
        this.description = description;
        this.statementIds.clear();
        this.statementIds.addAll(statementIds);
    }
}
