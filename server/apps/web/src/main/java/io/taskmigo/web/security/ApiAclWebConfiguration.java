package io.taskmigo.web.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class ApiAclWebConfiguration implements WebMvcConfigurer {

    private final ApiAclInterceptor interceptor;

    ApiAclWebConfiguration(ApiAclInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this.interceptor).addPathPatterns("/api/v*/**").order(Ordered.HIGHEST_PRECEDENCE);
    }
}
