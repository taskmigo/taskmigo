package io.taskmigo.authorization.statement.composition;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import io.taskmigo.authorization.statement.application.port.in.internal.StatementCommandService;
import io.taskmigo.authorization.statement.application.port.out.StatementCommandRepository;
import io.taskmigo.authorization.statement.application.port.out.StatementQueryRepository;
import io.taskmigo.authorization.statement.application.service.DefaultStatementCommandService;
import io.taskmigo.authorization.statement.application.service.DefaultStatementService;
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
