package io.taskmigo.auth.authorization.statement;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
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
    @Column(name = "target_type", nullable = false, length = 16)
    TargetType targetType;

    @Column(nullable = false, length = 16)
    String method;

    @Column(nullable = false, length = 2000)
    String path;

    @ElementCollection
    @CollectionTable(name = "statement_conditions", joinColumns = @JoinColumn(name = "statement_id"))
    @OrderColumn(name = "condition_index")
    @Column(name = "expression", nullable = false, length = 2000)
    List<String> conditions = new ArrayList<>();

    protected StatementEntity() {}

    StatementEntity(
        UUID id,
        String name,
        @Nullable String description,
        Effect effect,
        TargetType targetType,
        String method,
        String path,
        List<String> conditions
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.effect = effect;
        this.targetType = targetType;
        this.method = method;
        this.path = path;
        this.conditions.addAll(conditions);
    }

    public StatementInfo info() {
        return new StatementInfo(
            this.id,
            this.name,
            this.description,
            this.effect,
            new TargetInfo(this.targetType, new ApiInfo(this.method, this.path)),
            List.copyOf(this.conditions)
        );
    }

    public UUID id() {
        return this.id;
    }
}
