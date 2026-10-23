package io.taskmigo.eav.schema.configuration;

import io.taskmigo.eav.schema.application.EavSchemaRepository;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaServiceImpl;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class EavSchemaConfiguration {

    @Bean
    EavSchemaService eavSchemaService(EavSchemaRepository repository) {
        return new EavSchemaServiceImpl(repository, Clock.systemUTC());
    }
}
