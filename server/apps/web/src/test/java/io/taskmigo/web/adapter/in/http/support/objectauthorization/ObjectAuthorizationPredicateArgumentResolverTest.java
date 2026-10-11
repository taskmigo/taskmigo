package io.taskmigo.web.adapter.in.http.support.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.port.in.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.query.QuerySchemaView;
import java.lang.reflect.Method;
import java.util.List;
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
    private QuerySchemaView schema;

    @Mock
    private QuerySchemaView otherSchema;

    @Mock
    private ObjectAuthorizationPredicate<TestObject> predicate;

    @Mock
    private AuthorizationContext context;

    /**
     * Given: two schemas can describe the same object DTO and the handler declares one operation ID.
     * Expect: Object Authorization uses only the schema selected by the handler operation.
     */
    @Test
    @DisplayName("selects object authorization schema by handler operation")
    void shouldSelectSchemaWhenHandlerDeclaresOperation() throws NoSuchMethodException {
        // Arrange
        Method method = TestController.class.getDeclaredMethod("list", ObjectAuthorizationPredicate.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.otherSchema.operation()).thenReturn("test.objects.delete");
        when(this.authorization.<TestObject>authorize(this.context, this.schema)).thenReturn(this.predicate);
        ObjectAuthorizationPredicateArgumentResolver resolver = new ObjectAuthorizationPredicateArgumentResolver(
            this.authorization,
            List.of(this.otherSchema, this.schema)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthorizationContext.ATTRIBUTE, this.context);

        // Act
        Object resolved = resolver.resolveArgument(parameter, null, new ServletWebRequest(request), null);

        // Assert
        assertThat(resolved).isSameAs(this.predicate);
        verify(this.authorization).authorize(this.context, this.schema);
    }

    private static final class TestController {

        @QueryOperation("test.objects.list")
        void list(ObjectAuthorizationPredicate<TestObject> authorization) {}
    }

    private static final class TestObject {}
}
