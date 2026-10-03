package io.taskmigo.foundation.jackson;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/// Integrates Taskmigo's shared Jackson policy with Spring Boot-managed JSON mappers.
@AutoConfiguration(afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnClass(JsonMapperBuilderCustomizer.class)
public class TaskmigoJacksonAutoConfiguration {

    @Bean
    JsonMapperBuilderCustomizer taskmigoJsonMapperBuilderCustomizer() {
        return new TaskmigoJsonMapperBuilderCustomizer();
    }
}
