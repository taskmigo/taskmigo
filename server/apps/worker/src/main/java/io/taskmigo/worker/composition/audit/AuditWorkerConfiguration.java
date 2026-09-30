package io.taskmigo.worker.composition.audit;

import io.taskmigo.audit.adapter.out.jobrunr.AuditJobRunrJsonMapperFactory;
import io.taskmigo.audit.adapter.out.jobrunr.AuditJobRunrTransport;
import io.taskmigo.audit.application.port.in.append.AuditAppendService;
import org.jobrunr.utils.mapper.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AuditWorkerConfiguration {

    @Bean
    JsonMapper jobRunrJsonMapper() {
        return AuditJobRunrJsonMapperFactory.create();
    }

    @Bean
    AuditJobRunrTransport auditJobRunrTransport(AuditAppendService audits) {
        return new AuditJobRunrTransport(audits);
    }
}
