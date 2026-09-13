package io.taskmigo.rest.support.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
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
    private ObjectAuthorizationSchema<TestObject> schema;

    /**
     * Verifies that Object Authorization target metadata is derived from the actual Spring MVC handler mapping.
     *
     * Given: a versioned GET handler whose typed ObjectAuthorizationPredicate targets TestObject.
     * Expect: the materialized `/api/v0/objects` route resolves to the TestObject schema without manual registration.
     */
    @Test
    @DisplayName("derives an object schema route from a typed MVC handler")
    void shouldResolveSchemaWhenTypedHandlerMappingMatchesStatementTarget() throws NoSuchMethodException {
        // Arrange
        Method method = TestController.class.getDeclaredMethod("list", ObjectAuthorizationPredicate.class);
        HandlerMethod handler = new HandlerMethod(new TestController(), method);
        when(this.handlerMappings.getObject()).thenReturn(this.handlerMapping);
        when(this.handlerMapping.getHandlerMethods()).thenReturn(Map.of(this.mapping, handler));
        when(this.mapping.getVersionCondition()).thenReturn(this.version);
        when(this.version.getVersion()).thenReturn("0");
        when(this.mapping.getPatternValues()).thenReturn(Set.of("/api/v{version}/objects"));
        when(this.mapping.getMethodsCondition()).thenReturn(new RequestMethodsRequestCondition(RequestMethod.GET));
        when(this.schema.objectType()).thenReturn(TestObject.class);
        SpringMvcObjectAuthorizationTargetResolver resolver = new SpringMvcObjectAuthorizationTargetResolver(
            this.handlerMappings,
            List.of(this.schema)
        );

        // Act
        resolver.afterSingletonsInstantiated();
        List<ObjectAuthorizationSchema<?>> applicable = resolver.applicable("GET", "/api/v0/objects");

        // Assert
        assertThat(applicable).containsExactly(this.schema);
    }

    private static final class TestController {
        void list(ObjectAuthorizationPredicate<TestObject> authorization) {}
    }

    private static final class TestObject {}
}
