package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationResource.Effect;
import io.taskmigo.authorization.AuthorizationResource.FieldRule;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
        this.method = resource.match().method();
        this.path = resource.match().path();
        this.target = resource.target();
        this.effect = resource.effect();
        this.condition = resource.when();
        this.origin = origin;
    }

    void replace(Statement resource, Origin origin) {
        this.key = resource.key();
        this.name = resource.name();
        this.description = resource.description();
        this.method = resource.match().method();
        this.path = resource.match().path();
        this.target = resource.target();
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
        this.effect = resource.effect();
        this.fieldNames = String.join("\n", resource.names());
        this.condition = resource.when();
    }

    FieldRule resource() {
        return new FieldRule(this.effect, Arrays.asList(this.fieldNames.split("\\n", -1)), this.condition);
    }
}

interface AuthorizationStatementRepository extends JpaRepository<AuthorizationStatementEntity, UUID> {
    Optional<AuthorizationStatementEntity> findByOrganizationIdAndKey(UUID organizationId, String key);

    Optional<AuthorizationStatementEntity> findByOrganizationIdIsNullAndKey(String key);

    @Query(
        "select statement from AuthorizationStatementEntity statement " +
        "where statement.organizationId = :organizationId or statement.organizationId is null order by statement.key"
    )
    List<AuthorizationStatementEntity> findRelevant(@Param("organizationId") UUID organizationId);

    List<AuthorizationStatementEntity> findAllByOrganizationIdIsNullOrderByKey();
}

interface AuthorizationFieldRuleRepository extends JpaRepository<AuthorizationFieldRuleEntity, UUID> {
    List<AuthorizationFieldRuleEntity> findAllByStatementIdIn(Collection<UUID> statementIds);

    void deleteAllByStatementId(UUID statementId);
}
