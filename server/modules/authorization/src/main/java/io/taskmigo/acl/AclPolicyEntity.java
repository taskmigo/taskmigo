package io.taskmigo.acl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "acl_policies")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
final class AclPolicyEntity {

    @Id
    UUID id;

    @Column(name = "organization_id", nullable = false)
    UUID organizationId;

    @Column(nullable = false, length = 200)
    String name;

    @Column(nullable = false, length = 16)
    String kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    Map<String, Object> definition;

    protected AclPolicyEntity() {}

    AclPolicyEntity(UUID id, UUID organizationId, String name, String kind, Map<String, Object> definition) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.kind = kind;
        this.definition = Map.copyOf(definition);
    }

    void replace(String kind, Map<String, Object> definition) {
        this.kind = kind;
        this.definition = Map.copyOf(definition);
    }
}
