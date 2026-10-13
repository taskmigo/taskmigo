package io.taskmigo.web.api.v0.support.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.web.api.v0.support.response.ApiResponse;
import io.taskmigo.web.api.v0.support.response.ApiResponseFactory;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class ApiV0SecurityErrorRendererTest {

    private final ApiResponseFactory responses = mock(ApiResponseFactory.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final ApiV0SecurityErrorRenderer renderer = new ApiV0SecurityErrorRenderer(
        this.responses,
        this.objectMapper
    );

    @Test
    @DisplayName("supports only API v0 requests")
    void shouldSupportOnlyApiV0Requests() {
        MockHttpServletRequest v0 = new MockHttpServletRequest();
        v0.setRequestURI("/api/v0/users");
        MockHttpServletRequest v1 = new MockHttpServletRequest();
        v1.setRequestURI("/api/v1/users");

        assertThat(this.renderer.supports(v0)).isTrue();
        assertThat(this.renderer.supports(v1)).isFalse();
    }

    @Test
    @DisplayName("renders unauthorized failures with the v0 envelope")
    void shouldRenderUnauthorizedFailure() throws IOException {
        this.assertFailure(
            HttpStatus.UNAUTHORIZED,
            "security.unauthorized",
            "Authentication is required",
            "UNAUTHORIZED"
        );
    }

    @Test
    @DisplayName("renders forbidden failures with the v0 envelope")
    void shouldRenderForbiddenFailure() throws IOException {
        this.assertFailure(HttpStatus.FORBIDDEN, "security.forbidden", "Access is forbidden", "FORBIDDEN");
    }

    private void assertFailure(HttpStatus status, String messageCode, String messageText, String errorCode)
        throws IOException {
        @SuppressWarnings("unchecked")
        ApiResponse<Void, ApiResponse.BasicMeta> body = mock(ApiResponse.class);
        when(
            this.responses.failureBody(eq(status), eq(messageCode), eq(messageText), any(ApiResponse.Error.class))
        ).thenReturn(body);
        MockHttpServletResponse response = new MockHttpServletResponse();

        this.renderer.write(response, status, messageCode, messageText);

        assertThat(response.getStatus()).isEqualTo(status.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        ArgumentCaptor<ApiResponse.Error> error = ArgumentCaptor.forClass(ApiResponse.Error.class);
        verify(this.responses).failureBody(eq(status), eq(messageCode), eq(messageText), error.capture());
        assertThat(error.getValue().code()).isEqualTo(errorCode);
        verify(this.objectMapper).writeValue(eq(response.getOutputStream()), same(body));
    }
}
