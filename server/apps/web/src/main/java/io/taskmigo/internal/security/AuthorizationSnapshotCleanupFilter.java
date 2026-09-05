package io.taskmigo.internal.security;

import io.taskmigo.rest.support.objectauthorization.AuthorizationOperation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/// Removes the operation authorization snapshot when the servlet request completes.
@Component
final class AuthorizationSnapshotCleanupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            request.removeAttribute(AuthorizationOperation.SNAPSHOT_ATTRIBUTE);
        }
    }
}
