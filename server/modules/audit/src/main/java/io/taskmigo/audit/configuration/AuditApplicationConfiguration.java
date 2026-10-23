package io.taskmigo.audit.configuration;

import io.taskmigo.audit.application.AuditLogStore;
import io.taskmigo.audit.application.DefaultAuditAppendService;
import io.taskmigo.audit.application.DefaultAuditPrivacyService;
import io.taskmigo.audit.application.DefaultAuditQueryService;
import io.taskmigo.audit.application.append.AuditAppendService;
import io.taskmigo.audit.application.privacy.AuditPrivacyService;
import io.taskmigo.audit.application.query.AuditQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Wires Audit application services to their runtime implementations.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "taskmigo.audit", name = "enabled", havingValue = "true")
class AuditApplicationConfiguration {

    @Bean
    AuditAppendService defaultAuditAppendService(AuditLogStore logs) {
        return new DefaultAuditAppendService(logs);
    }

    @Bean
    AuditPrivacyService defaultAuditPrivacyService(AuditLogStore logs) {
        return new DefaultAuditPrivacyService(logs);
    }

    @Bean
    AuditQueryService defaultAuditQueryService(AuditLogStore logs) {
        return new DefaultAuditQueryService(logs);
    }
}
