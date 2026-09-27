package io.taskmigo.audit.composition;

import io.taskmigo.audit.application.port.in.api.AuditEventService;
import io.taskmigo.audit.application.port.in.api.AuditLogService;
import io.taskmigo.audit.application.port.in.internal.AuditAppendService;
import io.taskmigo.audit.application.port.out.AuditEventPublisher;
import io.taskmigo.audit.application.port.out.AuditLogAppendRepository;
import io.taskmigo.audit.application.port.out.AuditLogQueryRepository;
import io.taskmigo.audit.application.service.DefaultAuditAppendService;
import io.taskmigo.audit.application.service.DefaultAuditEventService;
import io.taskmigo.audit.application.service.DefaultAuditLogService;
import io.taskmigo.audit.model.AuditMutationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;

@Configuration(proxyBeanMethods = false)
class AuditApplicationConfiguration {

    @Bean
    AuditEventService auditEventService(AuditEventPublisher publisher) {
        return new DefaultAuditEventService(publisher);
    }

    @Bean
    AuditAppendService auditAppendService(AuditLogAppendRepository logs) {
        return new DefaultAuditAppendService(logs);
    }

    @Bean
    AuditLogService auditLogService(AuditLogQueryRepository logs) {
        return new DefaultAuditLogService(logs);
    }

    @Bean
    EventExternalizationConfiguration auditEventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing().selectByType(AuditMutationEvent.class).build();
    }
}
