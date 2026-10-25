package io.taskmigo.web.security.objectauthorization;

import io.taskmigo.authorization.object.AuthorizationContract;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.application.resolution.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/// Derives Object Authorization target metadata from Spring MVC routes and explicit Authorization Contracts.
@Component
@Primary
public final class SpringMvcObjectAuthorizationTargetResolver
    implements ObjectAuthorizationTargetResolver, SmartInitializingSingleton
{

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;
    private final List<ObjectAuthorizationSchema<?>> schemas;
    private List<Route> routes = List.of();

    public SpringMvcObjectAuthorizationTargetResolver(
        ObjectProvider<RequestMappingHandlerMapping> handlerMappings,
        List<ObjectAuthorizationSchema<?>> schemas
    ) {
        this.handlerMappings = handlerMappings;
        this.schemas = List.copyOf(schemas);
    }

    @Override
    public void afterSingletonsInstantiated() {
        this.routes = this.handlerMappings
            .getObject()
            .getHandlerMethods()
            .entrySet()
            .stream()
            .flatMap(entry -> this.routes(entry.getKey(), entry.getValue()).stream())
            .toList();
    }

    @Override
    public List<ObjectAuthorizationSchema<?>> applicable(String method, StatementTargetPathMatcher pathMatcher) {
        return this.routes
            .stream()
            .filter(route -> route.matches(method, pathMatcher))
            .map(route -> (ObjectAuthorizationSchema<?>) route.schema())
            .distinct()
            .toList();
    }

    private List<Route> routes(RequestMappingInfo mapping, HandlerMethod handler) {
        Optional<ObjectAuthorizationSchema<?>> schema = this.schema(handler);
        if (schema.isEmpty()) {
            return List.of();
        }
        ObjectAuthorizationSchema<?> declaredSchema = schema.orElseThrow();
        String version = mapping.getVersionCondition().getVersion();
        List<String> methods = mapping.getMethodsCondition().getMethods().isEmpty()
            ? List.of("*")
            : mapping.getMethodsCondition().getMethods().stream().map(RequestMethod::name).toList();
        return mapping
            .getPatternValues()
            .stream()
            .map(pattern -> version == null ? pattern : pattern.replace("{version}", version))
            .flatMap(route -> methods.stream().map(method -> new Route(method, route, declaredSchema)))
            .toList();
    }

    private Optional<ObjectAuthorizationSchema<?>> schema(HandlerMethod handler) {
        AuthorizationContract contract = handler.getMethodAnnotation(AuthorizationContract.class);
        if (contract == null) {
            return Optional.empty();
        }
        List<ObjectAuthorizationSchema<?>> matches = this.schemas
            .stream()
            .filter(contract.value()::isInstance)
            .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                "Expected exactly one Object Authorization schema for contract " +
                    contract.value().getName() +
                    ", found " +
                    matches.size()
            );
        }
        return Optional.of(matches.getFirst());
    }

    private record Route(String method, String path, ObjectAuthorizationSchema<?> schema) {
        private boolean matches(String statementMethod, StatementTargetPathMatcher pathMatcher) {
            return (
                ("*".equals(statementMethod) || "*".equals(this.method) || this.method.equals(statementMethod)) &&
                pathMatcher.matches(this.path)
            );
        }
    }
}
