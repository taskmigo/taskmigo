package io.taskmigo.audit.composition;

import io.taskmigo.audit.AuditEvent;
import io.taskmigo.audit.AuditLogService;
import io.taskmigo.audit.application.port.out.AuditLogRepository;
import io.taskmigo.audit.application.port.out.AuditTransactionRunner;
import io.taskmigo.audit.application.service.AuditLogAppender;
import io.taskmigo.audit.application.service.DefaultAuditLogService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

@Configuration(proxyBeanMethods = false)
class AuditApplicationConfiguration {

    @Bean
    AuditLogService defaultAuditLogService(AuditLogRepository logs, AuditTransactionRunner transactions) {
        return new DefaultAuditLogService(logs, transactions);
    }

    @Bean
    AuditLogAppender auditLogAppender(AuditLogRepository logs, AuditTransactionRunner transactions) {
        return new AuditLogAppender(logs, transactions);
    }

    @Bean
    EventExternalizationConfiguration auditEventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
            .selectByType(AuditEvent.class)
            .route(AuditEvent.class, event ->
                RoutingTarget.forTarget("taskmigo-audit").andKey(event.entityId().toString())
            )
            .build();
    }
}
