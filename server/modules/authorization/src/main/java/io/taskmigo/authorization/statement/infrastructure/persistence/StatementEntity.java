package io.taskmigo.authorization.statement.infrastructure.persistence;

import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.authorization.statement.domain.Statement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "statements")
@SuppressWarnings("NotNullFieldNotInitialized")
public class StatementEntity {

    @Id
    UUID id;

    @Column(nullable = false, unique = true)
    String code;

    @Column(length = 1000)
    @Nullable
    String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    io.taskmigo.authorization.statement.Effect effect;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    io.taskmigo.authorization.statement.Scope scope;

    @Column(nullable = false, length = 16)
    String method;

    @Column(nullable = false, length = 2000)
    String path;

    @Column(nullable = false, columnDefinition = "text")
    String policy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Nullable
    Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    @Nullable
    Instant updatedAt;

    protected StatementEntity() {}

    private StatementEntity(Statement statement) {
        this.id = statement.id();
        this.code = statement.code().value();
        this.update(statement);
    }

    static StatementEntity from(Statement statement) {
        return new StatementEntity(statement);
    }

    Statement toDomain() {
        return Statement.restore(
            this.id,
            this.code,
            this.description,
            this.effect,
            this.scope,
            this.method,
            this.path,
            this.policy
        );
    }

    void update(Statement statement) {
        this.description = statement.description();
        this.effect = statement.effect();
        this.scope = statement.scope();
        this.method = statement.target().method();
        this.path = statement.target().path();
        this.policy = statement.policy().source();
    }

    /// Returns the projection consumed by runtime authorization and public Statement queries.
    public StatementInfo info() {
        return new StatementInfo(
            this.id,
            this.code,
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

    public Instant createdAt() {
        return Objects.requireNonNull(this.createdAt, "persisted Statement created_at is not initialized");
    }

    public Instant updatedAt() {
        return Objects.requireNonNull(this.updatedAt, "persisted Statement updated_at is not initialized");
    }
}
