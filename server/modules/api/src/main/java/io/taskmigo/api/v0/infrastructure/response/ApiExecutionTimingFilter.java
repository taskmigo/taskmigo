package io.taskmigo.api.v0.infrastructure.response;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE)
final class ApiExecutionTimingFilter extends OncePerRequestFilter {

    static final String STARTED_AT_ATTRIBUTE = ApiExecutionTimingFilter.class.getName() + ".startedAt";
    static final String START_NANOS_ATTRIBUTE = ApiExecutionTimingFilter.class.getName() + ".startNanos";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v0/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        request.setAttribute(STARTED_AT_ATTRIBUTE, Instant.now());
        request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
        filterChain.doFilter(request, response);
    }
}
