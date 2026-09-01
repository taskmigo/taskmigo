package io.taskmigo.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
final class ApiAclInterceptor implements HandlerInterceptor {

    private final ApiAclSupport acl;

    ApiAclInterceptor(ApiAclSupport acl) {
        this.acl = acl;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw new AccessDeniedException(
            "Authentication is required"
        );
        this.acl.authorize(authentication, request, response);
        return true;
    }
}
