package io.taskmigo.eav.value.configuration;

import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavTransactionRunner;
import io.taskmigo.eav.schema.domain.EavValueValidator;
import io.taskmigo.eav.value.application.EavInstanceRepository;
import io.taskmigo.eav.value.application.EavInstanceService;
import io.taskmigo.eav.value.application.EavInstanceServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class EavValueConfiguration {

    @Bean
    EavInstanceService eavInstanceService(
        EavSchemaService schemas,
        EavInstanceRepository instances,
        EavTransactionRunner transactions
    ) {
        return new EavInstanceServiceImpl(schemas, instances, new EavValueValidator(), transactions);
    }
}
