package io.taskmigo.web.adapter.out.objectauthorization;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Primary;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/// Derives Object Authorization target metadata from Spring MVC handler mappings.
@Component
@Primary
public final class SpringMvcObjectAuthorizationTargetResolver
    implements ObjectAuthorizationTargetResolver, SmartInitializingSingleton
{

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;
    private final List<ObjectAuthorizationBinding<?>> bindings;
    private List<Route> routes = List.of();

    /// Creates a resolver from Spring MVC mappings and the resource-owned bindings.
    public SpringMvcObjectAuthorizationTargetResolver(
        ObjectProvider<RequestMappingHandlerMapping> handlerMappings,
        List<ObjectAuthorizationBinding<?>> bindings
    ) {
        this.handlerMappings = handlerMappings;
        this.bindings = List.copyOf(bindings);
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
    public List<ObjectAuthorizationBinding<?>> applicable(
        String method,
        StatementTargetPathMatcher pathMatcher
    ) {
        return this.routes
            .stream()
            .filter(route -> route.matches(method, pathMatcher))
            .<ObjectAuthorizationBinding<?>>map(Route::binding)
            .distinct()
            .toList();
    }

    private List<Route> routes(RequestMappingInfo mapping, HandlerMethod handler) {
        Optional<ObjectAuthorizationBinding<?>> binding = this.binding(handler);
        if (binding.isEmpty()) {
            return List.of();
        }
        ObjectAuthorizationBinding<?> declaredBinding = binding.orElseThrow();
        String version = mapping.getVersionCondition().getVersion();
        List<String> methods = mapping.getMethodsCondition().getMethods().isEmpty()
            ? List.of("*")
            : mapping.getMethodsCondition().getMethods().stream().map(RequestMethod::name).toList();
        return mapping
            .getPatternValues()
            .stream()
            .map(pattern -> version == null ? pattern : pattern.replace("{version}", version))
            .flatMap(route -> methods.stream().map(method -> new Route(method, route, declaredBinding)))
            .toList();
    }

    private Optional<ObjectAuthorizationBinding<?>> binding(HandlerMethod handler) {
        List<MethodParameter> predicateParameters = Arrays.stream(handler.getMethodParameters())
            .filter(parameter -> parameter.getParameterType() == ObjectAuthorizationPredicate.class)
            .toList();
        if (predicateParameters.isEmpty()) {
            return Optional.empty();
        }
        if (predicateParameters.size() != 1) {
            throw new IllegalStateException("A handler may declare only one ObjectAuthorizationPredicate");
        }
        Class<?> objectType = this.objectType(predicateParameters.getFirst());
        List<ObjectAuthorizationBinding<?>> matches = this.bindings
            .stream()
            .filter(candidate -> candidate.objectType().equals(objectType))
            .toList();
        if (matches.isEmpty()) {
            throw new IllegalStateException("No Object Authorization binding registered for " + objectType.getName());
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("multiple Object Authorization bindings registered for " + objectType.getName());
        }
        return Optional.of(
            matches.getFirst()
        );
    }

    private Class<?> objectType(MethodParameter parameter) {
        return Objects.requireNonNull(
            ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve(),
            "ObjectAuthorizationPredicate must declare an object type"
        );
    }

    private record Route(String method, String path, ObjectAuthorizationBinding<?> binding) {
        private boolean matches(String statementMethod, StatementTargetPathMatcher pathMatcher) {
            return (
                ("*".equals(statementMethod) || "*".equals(this.method) || this.method.equals(statementMethod)) &&
                pathMatcher.matches(this.path)
            );
        }
    }
}
