package io.taskmigo.web.composition.bff;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(BffProperties.class)
class BffConfiguration {

    @Bean
    FilterRegistrationBean<BffInternalAuthenticationFilter> bffInternalAuthenticationFilter(BffProperties properties) {
        var registration = new FilterRegistrationBean<>(
            new BffInternalAuthenticationFilter(properties.internalSecret())
        );
        registration.addUrlPatterns("/_internal/bff/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
