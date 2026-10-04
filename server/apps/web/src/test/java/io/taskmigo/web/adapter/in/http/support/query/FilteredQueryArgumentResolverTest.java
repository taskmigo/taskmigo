package io.taskmigo.web.adapter.in.http.support.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.SchemaContext;
import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.query.QueryBinding;
import io.taskmigo.query.QueryBindingResolver;
import io.taskmigo.query.QueryPredicate;
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
class FilteredQueryArgumentResolverTest {

    @Mock
    private FilterByCompiler filters;

    @Mock
    private QueryBindingResolver bindings;

    @Mock
    private ResourceSchema schema;

    @Mock
    private QueryBinding<TestQuery> binding;

    @Mock
    private QueryPredicate<TestQuery> predicate;

    @Test
    @DisplayName("resolves filtering against the runtime schema selected for the current authorization context")
    void shouldResolveFilteringAgainstRuntimeSchemaContext() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list", FilteredQuery.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        SchemaContext schemaContext = new SchemaContext(Map.of("templateId", "incident"));
        AuthorizationContext authorizationContext = new AuthorizationContext() {
            @Override
            public SchemaContext schemaContext() {
                return schemaContext;
            }
        };
        QueryBindingResolver.Resolution resolution = new QueryBindingResolver.Resolution(this.schema, this.binding);
        when(this.bindings.resolve(TestQuery.class, schemaContext)).thenReturn(resolution);
        doReturn(this.predicate).when(this.filters).compileUntyped(this.schema, this.binding, "object.priority >= 3");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthorizationContext.ATTRIBUTE, authorizationContext);
        request.setParameter("filterBy", "object.priority >= 3");

        Object resolved = new FilteredQueryArgumentResolver(this.bindings, this.filters).resolveArgument(
            parameter,
            null,
            new ServletWebRequest(request),
            null
        );

        assertThat(((FilteredQuery<?>) resolved).predicate()).isSameAs(this.predicate);
    }

    private static final class TestController {

        void list(FilteredQuery<TestQuery> query) {}
    }

    private static final class TestQuery {}
}
