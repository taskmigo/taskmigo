package io.taskmigo.audit.composition;

import io.taskmigo.audit.application.port.in.append.AuditAppendService;
import io.taskmigo.audit.application.port.in.privacy.AuditPrivacyService;
import io.taskmigo.audit.application.port.in.query.AuditQueryService;
import io.taskmigo.audit.application.port.out.AuditLogStore;
import io.taskmigo.audit.application.port.out.AuditTransactionRunner;
import io.taskmigo.audit.application.service.DefaultAuditAppendService;
import io.taskmigo.audit.application.service.DefaultAuditPrivacyService;
import io.taskmigo.audit.application.service.DefaultAuditQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "taskmigo.audit", name = "enabled", havingValue = "true")
class AuditApplicationConfiguration {

    @Bean
    AuditAppendService defaultAuditAppendService(AuditLogStore logs, AuditTransactionRunner transactions) {
        return new DefaultAuditAppendService(logs, transactions);
    }

    @Bean
    AuditPrivacyService defaultAuditPrivacyService(AuditLogStore logs, AuditTransactionRunner transactions) {
        return new DefaultAuditPrivacyService(logs, transactions);
    }

    @Bean
    AuditQueryService defaultAuditQueryService(AuditLogStore logs, AuditTransactionRunner transactions) {
        return new DefaultAuditQueryService(logs, transactions);
    }
}
