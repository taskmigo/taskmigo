package io.taskmigo.web.adapter.out.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationBindingResolver;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import io.taskmigo.language.SchemaContext;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.condition.VersionRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@ExtendWith(MockitoExtension.class)
class SpringMvcObjectAuthorizationTargetResolverTest {

    @Mock
    private ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    @Mock
    private RequestMappingHandlerMapping handlerMapping;

    @Mock
    private RequestMappingInfo mapping;

    @Mock
    private VersionRequestCondition version;

    @Mock
    private ObjectAuthorizationBindingResolver bindings;

    @Mock
    private ObjectAuthorizationBinding<TestObject> binding;

    @Test
    @DisplayName("resolves a typed MVC target using the effective runtime schema context")
    void shouldResolveSchemaWhenTypedHandlerMappingMatchesStatementTarget() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list", ObjectAuthorizationPredicate.class);
        HandlerMethod handler = new HandlerMethod(new TestController(), method);
        SchemaContext schemaContext = new SchemaContext(Map.of("templateId", "incident"));
        when(this.handlerMappings.getObject()).thenReturn(this.handlerMapping);
        when(this.handlerMapping.getHandlerMethods()).thenReturn(Map.of(this.mapping, handler));
        when(this.mapping.getVersionCondition()).thenReturn(this.version);
        when(this.version.getVersion()).thenReturn("0");
        when(this.mapping.getPatternValues()).thenReturn(Set.of("/api/v{version}/objects"));
        when(this.mapping.getMethodsCondition()).thenReturn(new RequestMethodsRequestCondition(RequestMethod.GET));
        doReturn(this.binding).when(this.bindings).resolve(TestObject.class, schemaContext);
        SpringMvcObjectAuthorizationTargetResolver resolver = new SpringMvcObjectAuthorizationTargetResolver(
            this.handlerMappings,
            this.bindings
        );

        resolver.afterSingletonsInstantiated();
        List<ObjectAuthorizationBinding<?>> applicable = resolver.applicable(
            "GET",
            StatementTargetPathMatcher.compile("/api/v0/objects"),
            schemaContext
        );

        assertThat(applicable).containsExactly(this.binding);
    }

    private static final class TestController {

        void list(ObjectAuthorizationPredicate<TestObject> authorization) {}
    }

    private static final class TestObject {}
}
