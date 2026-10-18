package io.taskmigo.web.api.support.query;

import io.taskmigo.query.FilterByCompiler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Wires Web-owned runtime integration for the persistence-neutral Query filtering library.
@Configuration(proxyBeanMethods = false)
class QuerySupportConfiguration {

    /// Exposes the filter compiler required by the HTTP query request compiler.
    @Bean
    FilterByCompiler filterByCompiler() {
        return new FilterByCompiler();
    }
}
