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

    @Test
    @DisplayName("caches one runtime field context per request contract")
    void shouldCacheRuntimeContextWhenContractIsResolvedRepeatedly() {
        QueryFieldContext context = new QueryFieldContext(Map.of("type", "incident"));
        AtomicInteger calls = new AtomicInteger();
        QueryFieldContextResolver resolver = new QueryFieldContextResolver(
            List.of(source(TestContract.class, context, calls))
        );
        NativeWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

        QueryFieldContext first = resolver.resolve(TestContract.class, request);
        QueryFieldContext second = resolver.resolve(TestContract.class, request);

        assertThat(first).isSameAs(context);
        assertThat(second).isSameAs(context);
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("defaults empty and rejects multiple runtime context sources")
    void shouldResolveZeroOrRejectMultipleRuntimeContextSources() {
        NativeWebRequest emptyRequest = new ServletWebRequest(new MockHttpServletRequest());
        QueryFieldContextResolver empty = new QueryFieldContextResolver(List.of());
        QueryFieldContext context = new QueryFieldContext(Map.of("scope", "one"));
        QueryFieldContextResolver duplicate = new QueryFieldContextResolver(
            List.of(
                source(TestContract.class, context, new AtomicInteger()),
                source(TestContract.class, context, new AtomicInteger())
            )
        );
        NativeWebRequest duplicateRequest = new ServletWebRequest(new MockHttpServletRequest());

        assertThat(empty.resolve(TestContract.class, emptyRequest)).isEqualTo(QueryFieldContext.empty());
        assertThatThrownBy(() -> duplicate.resolve(TestContract.class, duplicateRequest))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("multiple");
    }

    private static QueryFieldContextSource source(
        Class<?> contract,
        QueryFieldContext context,
        AtomicInteger calls
    ) {
        return new QueryFieldContextSource() {
            @Override
            public boolean supports(Class<?> candidate) {
                return contract == candidate;
            }

            @Override
            public QueryFieldContext resolve(Class<?> candidate, NativeWebRequest request) {
                calls.incrementAndGet();
                return context;
            }
        };
    }

    private interface TestContract {}
}
