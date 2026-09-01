package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationResource.Effect;
import io.taskmigo.authorization.AuthorizationResource.FieldRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "authorization_statement_field_rules")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationFieldRuleEntity {

    @Id
    UUID id;

    @Column(name = "statement_id", nullable = false)
    UUID statementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    Effect effect;

    @Column(name = "field_names", nullable = false, columnDefinition = "text")
    String fieldNames;

    @Nullable
    @Column(name = "condition_expression", length = 1024)
    String condition;

    protected AuthorizationFieldRuleEntity() {}

    AuthorizationFieldRuleEntity(UUID statementId, FieldRule resource) {
        this.id = UUID.randomUUID();
        this.statementId = statementId;
        this.effect = Objects.requireNonNull(resource.effect(), "Field rule effect is required");
        this.fieldNames = String.join("\n", Objects.requireNonNullElse(resource.names(), List.of()));
        this.condition = resource.when();
    }

    FieldRule resource() {
        return new FieldRule(this.effect, Arrays.asList(this.fieldNames.split("\\n", -1)), this.condition);
    }
}
