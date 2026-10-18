package io.taskmigo.web.api.support.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.query.QueryFieldContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

class QueryFieldContextResolverTest {

    /**
     * Verifies that one runtime Query context is reused throughout a request.
     *
     * Given: one matching context source and repeated resolution of the same operation in one request.
     * Expect: both callers receive the same context while the source is invoked only once.
     */
    @Test
    @DisplayName("caches one runtime Query field context per request operation")
    void shouldCacheRuntimeContextWhenOperationIsResolvedRepeatedly() {
        // Arrange
        QueryFieldContext context = new QueryFieldContext(Map.of("type", "incident"));
        AtomicInteger calls = new AtomicInteger();
        QueryFieldContextResolver resolver = new QueryFieldContextResolver(
            List.of(source("test.objects.list", context, calls))
        );
        NativeWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

        // Act
        QueryFieldContext first = resolver.resolve("test.objects.list", request);
        QueryFieldContext second = resolver.resolve("test.objects.list", request);

        // Assert
        assertThat(first).isSameAs(context);
        assertThat(second).isSameAs(context);
        assertThat(calls).hasValue(1);
    }

    /**
     * Verifies deterministic runtime Query context selection.
     *
     * Given: zero sources or multiple sources for one operation.
     * Expect: zero sources yields the empty context while ambiguous sources are rejected.
     */
    @Test
    @DisplayName("defaults empty and rejects multiple runtime Query context sources")
    void shouldResolveZeroOrRejectMultipleRuntimeContextSources() {
        // Arrange
        NativeWebRequest emptyRequest = new ServletWebRequest(new MockHttpServletRequest());
        QueryFieldContextResolver empty = new QueryFieldContextResolver(List.of());
        QueryFieldContext context = new QueryFieldContext(Map.of("scope", "one"));
        QueryFieldContextResolver duplicate = new QueryFieldContextResolver(
            List.of(
                source("test.objects.list", context, new AtomicInteger()),
                source("test.objects.list", context, new AtomicInteger())
            )
        );
        NativeWebRequest duplicateRequest = new ServletWebRequest(new MockHttpServletRequest());

        // Act / Assert
        assertThat(empty.resolve("test.objects.list", emptyRequest)).isEqualTo(QueryFieldContext.empty());
        assertThatThrownBy(() -> duplicate.resolve("test.objects.list", duplicateRequest))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("multiple");
    }

    private static QueryFieldContextSource source(String operation, QueryFieldContext context, AtomicInteger calls) {
        return new QueryFieldContextSource() {
            @Override
            public boolean supports(String candidate) {
                return operation.equals(candidate);
            }

            @Override
            public QueryFieldContext resolve(String candidate, NativeWebRequest request) {
                calls.incrementAndGet();
                return context;
            }
        };
    }
}
