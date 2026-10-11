package io.taskmigo.authorization.statement.configuration;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.statement.application.DefaultStatementCommandService;
import io.taskmigo.authorization.statement.application.DefaultStatementService;
import io.taskmigo.authorization.statement.application.StatementCommandRepository;
import io.taskmigo.authorization.statement.application.StatementQueryRepository;
import io.taskmigo.authorization.statement.application.api.StatementService;
import io.taskmigo.authorization.statement.application.internal.StatementCommandService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class StatementApplicationConfiguration {

    @Bean
    StatementCommandService defaultStatementCommandService(StatementCommandRepository statements) {
        return new DefaultStatementCommandService(statements);
    }

    @Bean
    StatementService defaultStatementService(
        StatementCommandService commands,
        StatementQueryRepository statements,
        TransactionRunner transactions
    ) {
        return new DefaultStatementService(commands, statements, transactions);
    }
}
