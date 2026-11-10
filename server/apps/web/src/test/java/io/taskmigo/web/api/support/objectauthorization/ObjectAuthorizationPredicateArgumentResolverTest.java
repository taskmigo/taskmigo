package io.taskmigo.web.api.support.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.AuthorizationContract;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.web.api.support.query.QueryFieldContextResolver;
import io.taskmigo.web.api.support.query.QueryFieldContextSource;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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
    private TestAuthorizationSchema schema;

    @Mock
    private OtherAuthorizationSchema otherSchema;

    @Mock
    private ObjectAuthorizationPredicate<TestObject> predicate;

    @Mock
    private AuthorizationContext context;

    @Test
    @DisplayName("selects the handler Authorization Contract and reuses its cached runtime field context")
    void shouldSelectAuthorizationContractAndReuseRuntimeContext() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list", ObjectAuthorizationPredicate.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        QueryFieldContext queryContext = new QueryFieldContext(Map.of("scope", "incident"));
        AtomicInteger sourceCalls = new AtomicInteger();
        QueryFieldContextSource source = new QueryFieldContextSource() {
            @Override
            public boolean supports(Class<?> contract) {
                return contract == TestAuthorizationSchema.class;
            }

            @Override
            public QueryFieldContext resolve(Class<?> contract, NativeWebRequest request) {
                sourceCalls.incrementAndGet();
                return queryContext;
            }
        };
        QueryFieldContextResolver contexts = new QueryFieldContextResolver(List.of(source));
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

    @Test
    @DisplayName("ignores client-supplied authorization data when resolving the server-owned predicate")
    void shouldIgnoreClientAuthorizationWhenRequestSuppliesAuthorizationData() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list", ObjectAuthorizationPredicate.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        QueryFieldContext queryContext = QueryFieldContext.empty();
        when(this.authorization.<TestObject>authorize(this.context, this.schema, queryContext)).thenReturn(
            this.predicate
        );
        ObjectAuthorizationPredicateArgumentResolver resolver = new ObjectAuthorizationPredicateArgumentResolver(
            this.authorization,
            List.of(this.schema),
            new QueryFieldContextResolver(List.of())
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("authorization", "client-allow-all");
        request.setContentType("application/json");
        request.setContent("{\"authorization\":\"client-allow-all\"}".getBytes(StandardCharsets.UTF_8));
        request.setAttribute(AuthorizationContext.ATTRIBUTE, this.context);
        ServletWebRequest webRequest = new ServletWebRequest(request);

        Object resolved = resolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(resolved).isSameAs(this.predicate);
        verify(this.authorization).authorize(this.context, this.schema, queryContext);
    }

    private static final class TestController {

        @AuthorizationContract(TestAuthorizationSchema.class)
        void list(ObjectAuthorizationPredicate<TestObject> authorization) {}
    }

    private interface TestAuthorizationSchema extends ObjectAuthorizationSchema<TestObject> {}

    private interface OtherAuthorizationSchema extends ObjectAuthorizationSchema<TestObject> {}

    private static final class TestObject {}
}
