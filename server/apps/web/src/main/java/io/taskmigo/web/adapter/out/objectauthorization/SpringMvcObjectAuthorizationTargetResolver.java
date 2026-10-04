package io.taskmigo.web.adapter.out.objectauthorization;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationBindingResolver;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import io.taskmigo.language.SchemaContext;
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
    private final ObjectAuthorizationBindingResolver bindings;
    private List<Route> routes = List.of();

    /// Creates a resolver from Spring MVC mappings and the runtime execution-binding resolver.
    public SpringMvcObjectAuthorizationTargetResolver(
        ObjectProvider<RequestMappingHandlerMapping> handlerMappings,
        ObjectAuthorizationBindingResolver bindings
    ) {
        this.handlerMappings = handlerMappings;
        this.bindings = bindings;
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
        return this.applicable(method, pathMatcher, SchemaContext.EMPTY);
    }

    @Override
    public List<ObjectAuthorizationBinding<?>> applicable(
        String method,
        StatementTargetPathMatcher pathMatcher,
        SchemaContext context
    ) {
        return this.routes
            .stream()
            .filter(route -> route.matches(method, pathMatcher))
            .<ObjectAuthorizationBinding<?>>map(route -> this.bindings.resolve(route.objectType(), context))
            .distinct()
            .toList();
    }

    private List<Route> routes(RequestMappingInfo mapping, HandlerMethod handler) {
        Optional<Class<?>> objectType = this.objectType(handler);
        if (objectType.isEmpty()) {
            return List.of();
        }
        Class<?> declaredType = objectType.orElseThrow();
        String version = mapping.getVersionCondition().getVersion();
        List<String> methods = mapping.getMethodsCondition().getMethods().isEmpty()
            ? List.of("*")
            : mapping.getMethodsCondition().getMethods().stream().map(RequestMethod::name).toList();
        return mapping
            .getPatternValues()
            .stream()
            .map(pattern -> version == null ? pattern : pattern.replace("{version}", version))
            .flatMap(route -> methods.stream().map(method -> new Route(method, route, declaredType)))
            .toList();
    }

    private Optional<Class<?>> objectType(HandlerMethod handler) {
        List<MethodParameter> predicateParameters = Arrays.stream(handler.getMethodParameters())
            .filter(parameter -> parameter.getParameterType() == ObjectAuthorizationPredicate.class)
            .toList();
        if (predicateParameters.isEmpty()) {
            return Optional.empty();
        }
        if (predicateParameters.size() != 1) {
            throw new IllegalStateException("A handler may declare only one ObjectAuthorizationPredicate");
        }
        return Optional.of(this.objectType(predicateParameters.getFirst()));
    }

    private Class<?> objectType(MethodParameter parameter) {
        return Objects.requireNonNull(
            ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve(),
            "ObjectAuthorizationPredicate must declare an object type"
        );
    }

    private record Route(String method, String path, Class<?> objectType) {
        private boolean matches(String statementMethod, StatementTargetPathMatcher pathMatcher) {
            return (
                ("*".equals(statementMethod) || "*".equals(this.method) || this.method.equals(statementMethod)) &&
                pathMatcher.matches(this.path)
            );
        }
    }
}
