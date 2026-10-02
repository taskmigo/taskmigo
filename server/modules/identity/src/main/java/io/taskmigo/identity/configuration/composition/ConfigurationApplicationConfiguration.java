package io.taskmigo.identity.configuration.composition;

import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.configuration.application.port.in.api.ConfigurationService;
import io.taskmigo.identity.configuration.application.port.out.ConfigurationStore;
import io.taskmigo.identity.configuration.application.service.DefaultConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ConfigurationApplicationConfiguration {

    @Bean
    ConfigurationService defaultConfigurationService(
        ConfigurationStore configurations,
        TransactionRunner transactions
    ) {
        return new DefaultConfigurationService(configurations, transactions);
    }
}
