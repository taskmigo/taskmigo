package io.taskmigo.auth.authorization.statement;

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

    @Column(columnDefinition = "text")
    @Nullable
    String policy;

    protected StatementEntity() {}

    StatementEntity(
        UUID id,
        String name,
        @Nullable String description,
        Effect effect,
        Scope scope,
        String method,
        String path,
        @Nullable String policy
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.effect = effect;
        this.scope = scope;
        this.method = method;
        this.path = path;
        this.policy = policy;
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
