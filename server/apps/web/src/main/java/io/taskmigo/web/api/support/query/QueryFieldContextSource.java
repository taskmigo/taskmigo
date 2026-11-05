package io.taskmigo.web.api.support.query;

import io.taskmigo.query.QueryFieldContext;
import org.springframework.web.context.request.NativeWebRequest;

/// Supplies server-owned runtime field metadata for one declared query or authorization contract.
public interface QueryFieldContextSource {
    @SuppressWarnings("NullableProblems")
    boolean supports(Class<?> contract);

    @SuppressWarnings("NullableProblems")
    QueryFieldContext resolve(Class<?> contract, NativeWebRequest request);
}
