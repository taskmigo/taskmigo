package io.taskmigo.web.security;

import io.taskmigo.authorization.AuthorizationDecision;
import io.taskmigo.authorization.AuthorizationDecision.Outcome;
import io.taskmigo.authorization.AuthorizationEngine;
import io.taskmigo.authorization.AuthorizationEngine.FieldDecision;
import io.taskmigo.authorization.AuthorizationEngine.ObjectPlan;
import io.taskmigo.authorization.EffectiveAuthorization;
import io.taskmigo.authorization.EffectiveAuthorizationResolver;
import io.taskmigo.identity.ServicePrincipalPermissions;
import io.taskmigo.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

@Component
public final class ApiAclSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiAclSupport.class);
    private static final String SNAPSHOT_ATTRIBUTE = ApiAclSupport.class.getName() + ".snapshot";
    private static final String CORRELATION_HEADER = "X-Request-ID";
    private static final String SYSTEM_RESOURCES_MANAGE_AUTHORITY =
        "PERMISSION_" + ServicePrincipalPermissions.SYSTEM_RESOURCES_MANAGE;
    private static final Pattern CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");

    private final EffectiveAuthorizationResolver resolver;
    private final AuthorizationEngine engine;
    private final UserService users;

    ApiAclSupport(EffectiveAuthorizationResolver resolver, AuthorizationEngine engine, UserService users) {
        this.resolver = resolver;
        this.engine = engine;
        this.users = users;
    }

    void authorize(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        String method = request.getMethod();
        String path = normalizedPath(request);
        String correlationId = correlationId(request);
        response.setHeader(CORRELATION_HEADER, correlationId);

        if (hasSystemResourceManagement(authentication)) {
            Snapshot snapshot = new Snapshot(true, null, Map.of(), method, path, correlationId, null, null);
            request.setAttribute(SNAPSHOT_ATTRIBUTE, snapshot);
            logBypass(snapshot);
            return;
        }

        UserService.UserInfo user = this.currentUser(authentication);
        Map<String, @Nullable Object> context = context(user, request, method, path);
        EffectiveAuthorization authorization = this.resolver.resolve(user.id());
        Snapshot snapshot = new Snapshot(
            false,
            authorization,
            context,
            method,
            path,
            correlationId,
            user.id(),
            user.organizationId()
        );
        request.setAttribute(SNAPSHOT_ATTRIBUTE, snapshot);

        AuthorizationDecision decision = this.engine.authorizeRequest(authorization, method, path, context);
        logDecision(snapshot, decision);
        if (decision.outcome() != Outcome.ALLOW) throw new AccessDeniedException(
            "Authorization Statements denied this request"
        );
    }

    public ObjectPlan objectPlan(HttpServletRequest request) {
        Snapshot snapshot = snapshot(request);
        if (snapshot.bypass()) return this.engine.unrestrictedObjects();
        EffectiveAuthorization authorization = snapshot.authorization();
        if (authorization == null) throw new IllegalStateException(
            "Authorization snapshot is missing the effective graph"
        );
        ObjectPlan plan = this.engine.planObjects(
            authorization,
            snapshot.method(),
            snapshot.path(),
            snapshot.context()
        );
        logDecision(snapshot, plan.decision());
        return plan;
    }

    public FieldDecision fieldDecision(
        ObjectPlan plan,
        Map<String, @Nullable Object> objectContext,
        Set<String> responseFields
    ) {
        return this.engine.authorizeFields(plan, objectContext, responseFields);
    }

    public void requireOrganization(Authentication authentication, UUID organizationId) {
        if (hasSystemResourceManagement(authentication)) return;
        UserService.UserInfo user = this.currentUser(authentication);
        if (!organizationId.equals(user.organizationId())) throw new AccessDeniedException(
            "Authorization resources can only be managed for the principal organization"
        );
    }

    private static Map<String, @Nullable Object> context(
        UserService.UserInfo user,
        HttpServletRequest request,
        String method,
        String path
    ) {
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        values.put("principal.id", user.id());
        values.put("principal.organizationId", user.organizationId());
        values.put("principal.username", user.username());
        values.put("principal.type", "user");
        values.put("request.method", method);
        values.put("request.path", path);

        Object pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables instanceof Map<?, ?> variables) {
            variables.forEach((key, value) -> {
                if (key instanceof String name && value instanceof String text) {
                    values.put("request.path." + name, scalar(text));
                }
            });
        }
        request.getParameterMap().forEach((name, entries) -> {
            if (entries.length == 1) values.put("request.query." + name, scalar(entries[0]));
        });
        return Collections.unmodifiableMap(values);
    }

    private UserService.UserInfo currentUser(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) throw new AccessDeniedException(
            "Authorization Statements require a user access token"
        );
        String userId = token.getToken().getClaimAsString("user_id");
        if (userId == null || userId.isBlank()) throw new AccessDeniedException(
            "Authorization Statements require a user principal"
        );
        try {
            return this.users.require(UUID.fromString(userId));
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid user principal", exception);
        }
    }

    private static Snapshot snapshot(HttpServletRequest request) {
        Object snapshot = request.getAttribute(SNAPSHOT_ATTRIBUTE);
        if (!(snapshot instanceof Snapshot value)) throw new IllegalStateException(
            "Object authorization requires the request authorization snapshot"
        );
        return value;
    }

    private static String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
    }

    private static String correlationId(HttpServletRequest request) {
        String supplied = request.getHeader(CORRELATION_HEADER);
        if (supplied != null && CORRELATION_ID.matcher(supplied).matches()) return supplied;
        return UUID.randomUUID().toString();
    }

    private static Object scalar(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return Boolean.valueOf(value);
            if (NUMBER.matcher(value).matches()) return new BigDecimal(value);
            return value;
        }
    }

    private static boolean hasSystemResourceManagement(Authentication authentication) {
        return authentication
            .getAuthorities()
            .stream()
            .anyMatch(authority -> SYSTEM_RESOURCES_MANAGE_AUTHORITY.equals(authority.getAuthority()));
    }

    private static void logBypass(Snapshot snapshot) {
        LOGGER.info(
            "authorization_decision correlationId={} userId={} organizationId={} method={} path={} target=request outcome=ALLOW reason=system-resource-manager",
            snapshot.correlationId(),
            snapshot.userId(),
            snapshot.organizationId(),
            snapshot.method(),
            snapshot.path()
        );
    }

    private static void logDecision(Snapshot snapshot, AuthorizationDecision decision) {
        LOGGER.info(
            "authorization_decision correlationId={} userId={} organizationId={} method={} path={} target={} outcome={} matchedStatements={} allowedBy={} deniedBy={} provenance={}",
            snapshot.correlationId(),
            snapshot.userId(),
            snapshot.organizationId(),
            snapshot.method(),
            snapshot.path(),
            decision.target(),
            decision.outcome(),
            decision.matchedStatements(),
            decision.allowedBy(),
            decision.deniedBy(),
            decision.provenance()
        );
    }

    private record Snapshot(
        boolean bypass,
        @Nullable EffectiveAuthorization authorization,
        Map<String, @Nullable Object> context,
        String method,
        String path,
        String correlationId,
        @Nullable UUID userId,
        @Nullable UUID organizationId
    ) {}
}
