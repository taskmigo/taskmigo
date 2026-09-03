package io.taskmigo.rest.support.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;

/// Renders authentication and authorization failures using an API-version-specific response contract.
///
/// [VersionedApiSecurityErrorHandler] delegates to the first configured renderer whose [#supports(HttpServletRequest)]
/// method matches the request. A matching renderer owns the complete response and prevents later renderers from
/// running.
public interface ApiSecurityErrorRenderer {
    /// Returns whether this renderer owns security failures for the request.
    ///
    /// @param request the request whose API version or response contract is being selected
    /// @return `true` when this renderer should handle the failure
    boolean supports(HttpServletRequest request);

    /// Writes the complete security-failure response selected by the shared handler.
    ///
    /// Implementations are responsible for the status, content type, and body required by their response contract.
    ///
    /// @param response the servlet response to complete
    /// @param status the authentication or authorization failure status
    /// @param messageCode the stable machine-readable failure code
    /// @param messageText the human-readable failure message
    /// @throws IOException if the response cannot be written
    void write(HttpServletResponse response, HttpStatus status, String messageCode, String messageText)
        throws IOException;
}
