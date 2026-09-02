package io.taskmigo.api.foundation.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/// Configures Spring MVC's built-in path-based API versioning for module-owned controllers.
@Configuration(proxyBeanMethods = false)
class ApiVersioningConfiguration implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
            .usePathSegment(1, path -> {
                return path.value().startsWith("/api/v");
            })
            .setVersionRequired(false);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v{version}", handlerType -> {
            RequestMapping mapping = handlerType.getAnnotation(RequestMapping.class);
            return mapping != null && mapping.version().equals("0");
        });
    }
}
