package io.taskmigo.web.adapter.in.http.support.query;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.query.QueryBinding;
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
class FilteredQueryArgumentResolverTest {

    @Mock
    private FilterByCompiler filters;

    @Mock
    private QueryBinding<TestQuery> binding;

    @Mock
    private QueryBinding<TestQuery> duplicateBinding;

    /**
     * Verifies that a controller query type cannot silently select an arbitrary execution binding.
     *
     * Given: two Query bindings registered for `TestQuery`.
     * Expect: argument resolution fails before compilation because semantic binding selection is ambiguous.
     */
    @Test
    @DisplayName("rejects duplicate query bindings for one controller type")
    void shouldRejectResolutionWhenQueryBindingsShareControllerType() throws NoSuchMethodException {
        // Arrange
        Method method = TestController.class.getDeclaredMethod("list", FilteredQuery.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        when(this.binding.queryType()).thenReturn(TestQuery.class);
        when(this.duplicateBinding.queryType()).thenReturn(TestQuery.class);
        FilteredQueryArgumentResolver resolver = new FilteredQueryArgumentResolver(
            List.of(this.binding, this.duplicateBinding),
            List.of(),
            this.filters
        );

        // Act + Assert
        assertThatThrownBy(() ->
            resolver.resolveArgument(parameter, null, new ServletWebRequest(new MockHttpServletRequest()), null)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("multiple query bindings");
    }

    private static final class TestController {

        void list(FilteredQuery<TestQuery> query) {}
    }

    private static final class TestQuery {}
}
