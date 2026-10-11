package io.taskmigo.identity.configuration.configuration;

import io.taskmigo.identity.application.TransactionRunner;
import io.taskmigo.identity.configuration.application.ConfigurationStore;
import io.taskmigo.identity.configuration.application.DefaultConfigurationService;
import io.taskmigo.identity.configuration.application.api.ConfigurationService;
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
