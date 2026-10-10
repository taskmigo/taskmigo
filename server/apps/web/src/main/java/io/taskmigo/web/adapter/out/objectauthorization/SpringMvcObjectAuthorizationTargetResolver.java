package io.taskmigo.web.adapter.out.objectauthorization;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import io.taskmigo.query.QuerySchemaView;
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
    private final List<QuerySchemaView> schemas;
    private List<Route> routes = List.of();

    public SpringMvcObjectAuthorizationTargetResolver(
        ObjectProvider<RequestMappingHandlerMapping> handlerMappings,
        List<QuerySchemaView> schemas
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
    public List<QuerySchemaView> applicable(String method, StatementTargetPathMatcher pathMatcher) {
        return this.routes
            .stream()
            .filter(route -> route.matches(method, pathMatcher))
            .map(Route::schema)
            .distinct()
            .toList();
    }

    private List<Route> routes(RequestMappingInfo mapping, HandlerMethod handler) {
        Optional<QuerySchemaView> schema = this.schema(handler);
        if (schema.isEmpty()) {
            return List.of();
        }
        QuerySchemaView declaredSchema = schema.orElseThrow();
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

    private Optional<QuerySchemaView> schema(HandlerMethod handler) {
        List<MethodParameter> parameters = Arrays.stream(handler.getMethodParameters())
            .filter(parameter -> parameter.getParameterType() == ObjectAuthorizationPredicate.class)
            .toList();
        if (parameters.isEmpty()) {
            return Optional.empty();
        }
        if (parameters.size() != 1) {
            throw new IllegalStateException("A handler may declare only one ObjectAuthorizationPredicate");
        }
        Class<?> objectType = objectType(parameters.getFirst());
        return Optional.of(
            this.schemas
                .stream()
                .filter(candidate -> candidate.operation().equals(objectType.getName()))
                .findFirst()
                .orElseThrow(() ->
                    new IllegalStateException("No Object Authorization Query Schema registered for " + objectType.getName())
                )
        );
    }

    private static Class<?> objectType(MethodParameter parameter) {
        return Objects.requireNonNull(
            ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve(),
            "ObjectAuthorizationPredicate must declare an object type"
        );
    }

    private record Route(String method, String path, QuerySchemaView schema) {
        private boolean matches(String statementMethod, StatementTargetPathMatcher pathMatcher) {
            return (
                ("*".equals(statementMethod) || "*".equals(this.method) || this.method.equals(statementMethod)) &&
                pathMatcher.matches(this.path)
            );
        }
    }
}
