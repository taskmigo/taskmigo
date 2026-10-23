package io.taskmigo.architecture.onionfixtures.application;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class ApplicationTransactionTemplateViolation {

    private final TransactionTemplate transactions;

    public ApplicationTransactionTemplateViolation(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void execute() {
        this.transactions.executeWithoutResult(status -> {});
    }
}
