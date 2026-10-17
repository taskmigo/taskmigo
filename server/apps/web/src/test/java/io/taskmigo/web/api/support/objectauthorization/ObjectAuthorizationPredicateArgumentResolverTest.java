package io.taskmigo.web.api.support.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.query.QuerySchemaView;
import io.taskmigo.web.api.support.query.QueryFieldContextResolver;
import io.taskmigo.web.api.support.query.QueryFieldContextSource;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
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

    @Test
    @DisplayName("selects the handler schema and reuses the cached runtime Query context for Object Authorization")
    void shouldSelectSchemaAndReuseRuntimeContext() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list", ObjectAuthorizationPredicate.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        QueryFieldContext queryContext = new QueryFieldContext(Map.of("scope", "incident"));
        AtomicInteger sourceCalls = new AtomicInteger();
        QueryFieldContextSource source = new QueryFieldContextSource() {
            @Override
            public boolean supports(String operation) {
                return operation.equals("test.objects.list");
            }

            @Override
            public QueryFieldContext resolve(String operation, NativeWebRequest request) {
                sourceCalls.incrementAndGet();
                return queryContext;
            }
        };
        QueryFieldContextResolver contexts = new QueryFieldContextResolver(List.of(source));
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.otherSchema.operation()).thenReturn("test.objects.delete");
        when(this.authorization.<TestObject>authorize(this.context, this.schema, queryContext)).thenReturn(
            this.predicate
        );
        ObjectAuthorizationPredicateArgumentResolver resolver = new ObjectAuthorizationPredicateArgumentResolver(
            this.authorization,
            List.of(this.otherSchema, this.schema),
            contexts
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthorizationContext.ATTRIBUTE, this.context);
        ServletWebRequest webRequest = new ServletWebRequest(request);

        Object first = resolver.resolveArgument(parameter, null, webRequest, null);
        Object second = resolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(first).isSameAs(this.predicate);
        assertThat(second).isSameAs(this.predicate);
        assertThat(sourceCalls).hasValue(1);
        verify(this.authorization, times(2)).authorize(this.context, this.schema, queryContext);
    }

    private static final class TestController {

        @QueryOperation("test.objects.list")
        void list(ObjectAuthorizationPredicate<TestObject> authorization) {}
    }

    private static final class TestObject {}
}
