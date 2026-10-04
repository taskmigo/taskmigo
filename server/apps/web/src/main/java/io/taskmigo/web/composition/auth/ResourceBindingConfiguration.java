package io.taskmigo.web.composition.auth;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationBindingResolver;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceSchemaResolver;
import io.taskmigo.language.ResourceType;
import io.taskmigo.query.QueryBinding;
import io.taskmigo.query.QueryBindingResolver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Supplies replaceable default runtime resource-schema and execution-binding resolvers for the web application.
@Configuration(proxyBeanMethods = false)
class ResourceBindingConfiguration {

    @Bean
    @ConditionalOnMissingBean(ResourceSchemaResolver.class)
    ResourceSchemaResolver resourceSchemaResolver(List<ResourceSchema> schemas) {
        Map<ResourceType, ResourceSchema> declared = new HashMap<>();
        for (ResourceSchema schema : schemas) {
            if (declared.putIfAbsent(schema.type(), schema) != null) {
                throw new IllegalStateException(
                    "multiple ResourceSchema beans registered for " +
                        schema.type().value() +
                        "; provide a context-aware ResourceSchemaResolver"
                );
            }
        }
        return ResourceSchemaResolver.fixed(declared);
    }

    @Bean
    @ConditionalOnMissingBean(QueryBindingResolver.class)
    QueryBindingResolver queryBindingResolver(List<QueryBinding<?>> bindings, ResourceSchemaResolver schemas) {
        return QueryBindingResolver.registered(bindings, schemas);
    }

    @Bean
    @ConditionalOnMissingBean(ObjectAuthorizationBindingResolver.class)
    ObjectAuthorizationBindingResolver objectAuthorizationBindingResolver(
        List<ObjectAuthorizationBinding<?>> bindings,
        ResourceSchemaResolver schemas
    ) {
        return ObjectAuthorizationBindingResolver.registered(bindings, schemas);
    }
}
