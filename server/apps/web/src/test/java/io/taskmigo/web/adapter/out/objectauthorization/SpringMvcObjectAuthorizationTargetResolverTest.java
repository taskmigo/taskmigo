package io.taskmigo.web.adapter.out.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
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
    private ObjectAuthorizationBinding<TestObject> binding;

    @Mock
    private ObjectAuthorizationBinding<TestObject> duplicateBinding;

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
        when(this.binding.objectType()).thenReturn(TestObject.class);
        SpringMvcObjectAuthorizationTargetResolver resolver = new SpringMvcObjectAuthorizationTargetResolver(
            this.handlerMappings,
            List.of(this.binding)
        );

        // Act
        resolver.afterSingletonsInstantiated();
        List<ObjectAuthorizationBinding<?>> applicable = resolver.applicable(
            "GET",
            StatementTargetPathMatcher.compile("/api/v0/objects")
        );

        // Assert
        assertThat(applicable).containsExactly(this.binding);
    }

    /**
     * Verifies that MVC route discovery does not silently choose one of several bindings for the same handler type.
     *
     * Given: a typed handler and two bindings registered for `TestObject`.
     * Expect: route initialization fails with an unambiguous binding-registration error.
     */
    @Test
    @DisplayName("rejects duplicate bindings for one typed MVC handler")
    void shouldRejectInitializationWhenTypedHandlerHasDuplicateBindings() throws NoSuchMethodException {
        // Arrange
        Method method = TestController.class.getDeclaredMethod("list", ObjectAuthorizationPredicate.class);
        HandlerMethod handler = new HandlerMethod(new TestController(), method);
        when(this.handlerMappings.getObject()).thenReturn(this.handlerMapping);
        when(this.handlerMapping.getHandlerMethods()).thenReturn(Map.of(this.mapping, handler));
        when(this.binding.objectType()).thenReturn(TestObject.class);
        when(this.duplicateBinding.objectType()).thenReturn(TestObject.class);
        SpringMvcObjectAuthorizationTargetResolver resolver = new SpringMvcObjectAuthorizationTargetResolver(
            this.handlerMappings,
            List.of(this.binding, this.duplicateBinding)
        );

        // Act + Assert
        assertThatThrownBy(resolver::afterSingletonsInstantiated)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("multiple Object Authorization bindings");
    }

    private static final class TestController {

        void list(ObjectAuthorizationPredicate<TestObject> authorization) {}
    }

    private static final class TestObject {}
}
