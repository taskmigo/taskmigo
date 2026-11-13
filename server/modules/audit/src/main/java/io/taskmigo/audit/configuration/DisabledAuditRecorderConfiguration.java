package io.taskmigo.audit.configuration;

import io.taskmigo.audit.application.record.AuditRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Provides an explicit no-op recorder only when the application disables auditing.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "taskmigo.audit", name = "enabled", havingValue = "false", matchIfMissing = true)
class DisabledAuditRecorderConfiguration {

    @Bean
    AuditRecorder disabledAuditRecorder() {
        return (entityType, entityId, operation, changes) -> {};
    }
}
