package io.taskmigo.web.api.support.query;

import io.taskmigo.query.QueryFieldContext;
import org.springframework.web.context.request.NativeWebRequest;

/// Supplies server-owned runtime Query field metadata for one operation and request.
public interface QueryFieldContextSource {
    @SuppressWarnings("NullableProblems")
    boolean supports(String operation);

    @SuppressWarnings("NullableProblems")
    QueryFieldContext resolve(String operation, NativeWebRequest request);
}
