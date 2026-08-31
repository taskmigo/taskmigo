package io.taskmigo.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class ApiAclRequestFilter extends OncePerRequestFilter {

    private final ApiAclSupport acl;

    ApiAclRequestFilter(ApiAclSupport acl) {
        this.acl = acl;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (
            authentication != null &&
            authentication.isAuthenticated() &&
            !this.acl.isRequestAllowed(authentication, request.getMethod(), request.getServletPath())
        ) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "ACL denied the request");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
