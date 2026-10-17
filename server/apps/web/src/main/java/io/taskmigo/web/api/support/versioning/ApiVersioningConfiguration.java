package io.taskmigo.web.api.support.versioning;

import io.taskmigo.web.api.support.objectauthorization.AuthorizationContextArgumentResolver;
import io.taskmigo.web.api.support.objectauthorization.ObjectAuthorizationPredicateArgumentResolver;
import io.taskmigo.web.api.support.query.FilteredQueryArgumentResolver;
import io.taskmigo.web.api.support.query.QueryFieldContextArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/// Configures Spring MVC's built-in path-based API versioning for module-owned controllers.
@Configuration(proxyBeanMethods = false)
class ApiVersioningConfiguration implements WebMvcConfigurer {

    private final AuthorizationContextArgumentResolver authorizationContext;
    private final ObjectAuthorizationPredicateArgumentResolver objectAuthorization;
    private final FilteredQueryArgumentResolver filteredQuery;
    private final QueryFieldContextArgumentResolver queryFieldContext;

    ApiVersioningConfiguration(
        AuthorizationContextArgumentResolver authorizationContext,
        ObjectAuthorizationPredicateArgumentResolver objectAuthorization,
        FilteredQueryArgumentResolver filteredQuery,
        QueryFieldContextArgumentResolver queryFieldContext
    ) {
        this.authorizationContext = authorizationContext;
        this.objectAuthorization = objectAuthorization;
        this.filteredQuery = filteredQuery;
        this.queryFieldContext = queryFieldContext;
    }

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.usePathSegment(1, path -> path.value().startsWith("/api/v")).setVersionRequired(false);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v{version}", handlerType -> {
            RequestMapping mapping = handlerType.getAnnotation(RequestMapping.class);

            return mapping != null && mapping.version().equals("0");
        });
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this.authorizationContext);
        resolvers.add(this.objectAuthorization);
        resolvers.add(this.filteredQuery);
        resolvers.add(this.queryFieldContext);
    }
}
