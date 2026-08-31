package io.taskmigo.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "acl_statements")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class StatementEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 16)
    String origin;

    @Nullable
    @Column(name = "organization_id")
    UUID organizationId;

    @Column(name = "statement_key", nullable = false, length = 100)
    String key;

    @Column(nullable = false, length = 200)
    String name;

    @Nullable
    @Column(length = 1000)
    String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    Map<String, Object> definition;

    protected StatementEntity() {}

    StatementEntity(
        UUID id,
        String origin,
        @Nullable UUID organizationId,
        String key,
        String name,
        @Nullable String description,
        Map<String, Object> definition
    ) {
        this.id = id;
        this.origin = origin;
        this.organizationId = organizationId;
        this.key = key;
        this.name = name;
        this.description = description;
        this.definition = Map.copyOf(definition);
    }

    void replace(String name, @Nullable String description, Map<String, Object> definition) {
        this.name = name;
        this.description = description;
        this.definition = Map.copyOf(definition);
    }
}
