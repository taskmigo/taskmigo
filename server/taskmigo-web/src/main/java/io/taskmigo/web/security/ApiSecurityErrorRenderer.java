package io.taskmigo.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;

public interface ApiSecurityErrorRenderer {

    boolean supports(HttpServletRequest request);

    void write(HttpServletResponse response, HttpStatus status, String messageCode, String messageText) throws IOException;
}
