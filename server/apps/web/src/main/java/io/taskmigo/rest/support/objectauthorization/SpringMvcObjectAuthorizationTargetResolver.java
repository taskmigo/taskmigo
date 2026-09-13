package io.taskmigo.rest.support.objectauthorization;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.spi.ObjectAuthorizationTargetResolver;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
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
    implements ObjectAuthorizationTargetResolver, SmartInitializingSingleton {

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;
    private final List<ObjectAuthorizationSchema<?>> schemas;
    private List<Route> routes = List.of();

    /// Creates a resolver from Spring MVC mappings and the resource-owned schemas.
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
    public List<ObjectAuthorizationSchema<?>> applicable(String method, String path) {
        return this.routes
            .stream()
            .filter(route -> route.matches(method, path))
            .<ObjectAuthorizationSchema<?>>map(Route::schema)
            .distinct()
            .toList();
    }

    private List<Route> routes(RequestMappingInfo mapping, HandlerMethod handler) {
        ObjectAuthorizationSchema<?> schema = this.schema(handler);
        if (schema == null) {
            return List.of();
        }
        String version = mapping.getVersionCondition().getVersion();
        List<String> methods = mapping.getMethodsCondition().getMethods().isEmpty()
            ? List.of("*")
            : mapping.getMethodsCondition().getMethods().stream().map(RequestMethod::name).toList();
        return mapping
            .getPatternValues()
            .stream()
            .map(pattern -> version == null ? pattern : pattern.replace("{version}", version))
            .flatMap(route -> methods.stream().map(method -> new Route(method, route, schema)))
            .toList();
    }

    private ObjectAuthorizationSchema<?> schema(HandlerMethod handler) {
        List<Class<?>> objectTypes = Arrays.stream(handler.getMethodParameters())
            .filter(parameter -> parameter.getParameterType() == ObjectAuthorizationPredicate.class)
            .map(this::objectType)
            .toList();
        if (objectTypes.isEmpty()) {
            return null;
        }
        if (objectTypes.size() != 1) {
            throw new IllegalStateException("A handler may declare only one ObjectAuthorizationPredicate");
        }
        Class<?> objectType = objectTypes.getFirst();
        return this.schemas
            .stream()
            .filter(candidate -> candidate.objectType().equals(objectType))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No Object Authorization Schema registered for " + objectType.getName()));
    }

    private Class<?> objectType(MethodParameter parameter) {
        return Objects.requireNonNull(
            ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve(),
            "ObjectAuthorizationPredicate must declare an object type"
        );
    }

    private record Route(String method, String path, ObjectAuthorizationSchema<?> schema) {
        private boolean matches(String statementMethod, String statementPath) {
            return (
                ("*".equals(statementMethod) || "*".equals(this.method) || this.method.equals(statementMethod)) &&
                Pattern.matches(statementPath, this.path)
            );
        }
    }
}
