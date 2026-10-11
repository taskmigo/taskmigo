package io.taskmigo.web.adapter.in.http.support.query;

import io.taskmigo.query.FilterByCompiler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Wires Web-owned runtime integration for the persistence-neutral Query filtering library.
@Configuration(proxyBeanMethods = false)
class QuerySupportConfiguration {

    /// Exposes the filter compiler required by the Spring MVC Query argument resolver.
    @Bean
    FilterByCompiler filterByCompiler() {
        return new FilterByCompiler();
    }
}
