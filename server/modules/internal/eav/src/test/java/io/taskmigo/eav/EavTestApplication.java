package io.taskmigo.eav;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "io.taskmigo.eav")
@EnableJpaRepositories(basePackages = "io.taskmigo.eav")
@ComponentScan(
    basePackages = "io.taskmigo.eav",
    excludeFilters = @Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
)
public class EavTestApplication {}
