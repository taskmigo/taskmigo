package io.taskmigo.identity.persistence.statement;

import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementDefinition;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "statements")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
public class StatementEntity {

    @Id
    UUID id;

    @Column(nullable = false, unique = true)
    String name;

    @Column(length = 1000)
    @Nullable
    String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    Effect effect;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    Scope scope;

    @Column(nullable = false, length = 16)
    String method;

    @Column(nullable = false, length = 2000)
    String path;

    @Column(nullable = false, columnDefinition = "text")
    String policy;

    protected StatementEntity() {}

    public StatementEntity(UUID id, StatementDefinition definition) {
        this.id = id;
        this.name = definition.name();
        this.description = definition.description();
        this.effect = definition.effect();
        this.scope = definition.scope();
        this.method = definition.method();
        this.path = definition.path();
        this.policy = definition.policy();
    }

    public StatementEntity(
        UUID id,
        String name,
        @Nullable String description,
        Effect effect,
        Scope scope,
        String method,
        String path,
        String policy
    ) {
        this(id, new StatementDefinition(name, description, effect, scope, method, path, policy));
    }

    public void update(StatementDefinition definition) {
        this.description = definition.description();
        this.effect = definition.effect();
        this.scope = definition.scope();
        this.method = definition.method();
        this.path = definition.path();
        this.policy = definition.policy();
    }

    public StatementInfo info() {
        return new StatementInfo(
            this.id,
            this.name,
            this.description,
            this.effect,
            this.scope,
            new TargetInfo(new ApiInfo(this.method, this.path)),
            this.policy
        );
    }

    public UUID id() {
        return this.id;
    }
}
