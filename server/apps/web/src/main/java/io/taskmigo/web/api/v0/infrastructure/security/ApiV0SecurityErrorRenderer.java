package io.taskmigo.web.api.v0.infrastructure.security;

import io.taskmigo.api.foundation.v0.infrastructure.response.ApiResponse;
import io.taskmigo.api.foundation.v0.infrastructure.response.ApiResponseFactory;
import io.taskmigo.web.security.ApiSecurityErrorRenderer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
final class ApiV0SecurityErrorRenderer implements ApiSecurityErrorRenderer {

    private final ApiResponseFactory responses;
    private final ObjectMapper objectMapper;

    ApiV0SecurityErrorRenderer(ApiResponseFactory responses, ObjectMapper objectMapper) {
        this.responses = responses;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v0/");
    }

    @Override
    public void write(HttpServletResponse response, HttpStatus status, String messageCode, String messageText)
        throws IOException {
        String errorCode = status == HttpStatus.UNAUTHORIZED ? "UNAUTHORIZED" : "FORBIDDEN";
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        this.objectMapper.writeValue(
            response.getOutputStream(),
            this.responses.failureBody(
                status,
                messageCode,
                messageText,
                new ApiResponse.Error(errorCode, messageText, null)
            )
        );
    }
}
