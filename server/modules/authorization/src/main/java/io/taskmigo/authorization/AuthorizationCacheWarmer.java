package io.taskmigo.authorization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AuthorizationCacheWarmer implements ApplicationRunner {

    private final AuthorizationCompiler compiler;
    private final AuthorizationStatementRepository statements;
    private final AuthorizationFieldRuleRepository fieldRules;

    AuthorizationCacheWarmer(
        AuthorizationCompiler compiler,
        AuthorizationStatementRepository statements,
        AuthorizationFieldRuleRepository fieldRules
    ) {
        this.compiler = compiler;
        this.statements = statements;
        this.fieldRules = fieldRules;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        List<AuthorizationStatementEntity> statementEntities = this.statements.findAll();
        Set<UUID> statementIds = statementEntities
            .stream()
            .map(statement -> statement.id)
            .collect(java.util.stream.Collectors.toSet());
        Map<UUID, List<AuthorizationFieldRuleEntity>> fieldsByStatement = new HashMap<>();
        this.fieldRules
            .findAllByStatementIdIn(statementIds)
            .forEach(field -> fieldsByStatement.computeIfAbsent(field.statementId, ignored -> new ArrayList<>()).add(field));
        for (AuthorizationStatementEntity statement : statementEntities) {
            AuthorizationResource.Statement resource = statement.resource(
                fieldsByStatement.getOrDefault(statement.id, List.of())
            );
            this.compiler.compileCached(statement.id, resource, statement.origin);
        }
    }
}
