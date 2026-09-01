package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationResource.Effect;
import io.taskmigo.authorization.AuthorizationResource.Match;
import io.taskmigo.authorization.AuthorizationResource.Origin;
import io.taskmigo.authorization.AuthorizationResource.Statement;
import io.taskmigo.authorization.AuthorizationResource.Target;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "authorization_statements")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationStatementEntity {

    @Id
    UUID id;

    @Nullable
    @Column(name = "organization_id")
    UUID organizationId;

    @Column(name = "statement_key", nullable = false, length = 128)
    String key;

    @Nullable
    @Column(length = 200)
    String name;

    @Nullable
    @Column(length = 1000)
    String description;

    @Column(name = "match_method", nullable = false, length = 16)
    String method;

    @Column(name = "match_path", nullable = false, length = 512)
    String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    Target target;

    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    Effect effect;

    @Nullable
    @Column(name = "condition_expression", length = 1024)
    String condition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    Origin origin;

    protected AuthorizationStatementEntity() {}

    AuthorizationStatementEntity(UUID id, @Nullable UUID organizationId, Statement resource, Origin origin) {
        this.id = id;
        this.organizationId = organizationId;
        this.key = resource.key();
        this.name = resource.name();
        this.description = resource.description();
        Match match = Objects.requireNonNull(resource.match(), "match is required");
        this.method = match.method();
        this.path = match.path();
        this.target = Objects.requireNonNull(resource.target(), "target is required");
        this.effect = resource.effect();
        this.condition = resource.when();
        this.origin = origin;
    }

    void replace(Statement resource, Origin origin) {
        this.key = resource.key();
        this.name = resource.name();
        this.description = resource.description();
        Match match = Objects.requireNonNull(resource.match(), "match is required");
        this.method = match.method();
        this.path = match.path();
        this.target = Objects.requireNonNull(resource.target(), "target is required");
        this.effect = resource.effect();
        this.condition = resource.when();
        this.origin = origin;
    }

    Statement resource(List<AuthorizationFieldRuleEntity> rules) {
        return new Statement(
            this.key,
            this.name,
            this.description,
            new Match(this.method, this.path),
            this.target,
            this.effect,
            this.condition,
            rules.stream().map(AuthorizationFieldRuleEntity::resource).toList()
        );
    }
}
