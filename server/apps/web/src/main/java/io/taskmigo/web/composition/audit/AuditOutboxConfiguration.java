package io.taskmigo.web.composition.audit;

import io.taskmigo.audit.adapter.out.jobrunr.AuditJobRunrJsonMapperFactory;
import io.taskmigo.audit.adapter.out.jobrunr.AuditJobRunrTransport;
import io.taskmigo.audit.application.port.in.append.AuditAppendService;
import io.taskmigo.audit.event.AuditEvent;
import org.jobrunr.utils.mapper.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;

@Configuration(proxyBeanMethods = false)
class AuditOutboxConfiguration {

    @Bean
    JsonMapper jobRunrJsonMapper() {
        return AuditJobRunrJsonMapperFactory.create();
    }

    @Bean
    EventExternalizationConfiguration auditEventExternalization() {
        return EventExternalizationConfiguration.externalizing().selectByType(AuditEvent.class).build();
    }

    @Bean
    AuditJobRunrTransport auditJobRunrTransport(AuditAppendService audits) {
        return new AuditJobRunrTransport(audits);
    }
}
