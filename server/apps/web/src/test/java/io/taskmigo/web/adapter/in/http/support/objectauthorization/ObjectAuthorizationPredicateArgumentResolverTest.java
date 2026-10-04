package io.taskmigo.web.adapter.in.http.support.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationBindingResolver;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.port.in.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.language.SchemaContext;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

@ExtendWith(MockitoExtension.class)
class ObjectAuthorizationPredicateArgumentResolverTest {

    @Mock
    private ObjectAuthorization authorization;

    @Mock
    private ObjectAuthorizationBindingResolver bindings;

    @Mock
    private ObjectAuthorizationBinding<TestObject> binding;

    @Mock
    private ObjectAuthorizationPredicate<TestObject> predicate;

    @Test
    @DisplayName("resolves object authorization through the runtime schema selected for the current request")
    void shouldResolvePredicateThroughRuntimeSchemaContext() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list", ObjectAuthorizationPredicate.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        SchemaContext schemaContext = new SchemaContext(Map.of("templateId", "incident"));
        AuthorizationContext context = new AuthorizationContext() {
            @Override
            public SchemaContext schemaContext() {
                return schemaContext;
            }
        };
        doReturn(this.binding).when(this.bindings).resolve(TestObject.class, schemaContext);
        when(this.authorization.authorize(context, this.binding)).thenReturn(this.predicate);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthorizationContext.ATTRIBUTE, context);

        Object resolved = new ObjectAuthorizationPredicateArgumentResolver(
            this.authorization,
            this.bindings
        ).resolveArgument(parameter, null, new ServletWebRequest(request), null);

        assertThat(resolved).isSameAs(this.predicate);
    }

    private static final class TestController {

        void list(ObjectAuthorizationPredicate<TestObject> authorization) {}
    }

    private static final class TestObject {}
}
