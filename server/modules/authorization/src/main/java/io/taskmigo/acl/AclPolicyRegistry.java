package io.taskmigo.acl;

import io.taskmigo.acl.AclExpression.Eq;
import io.taskmigo.acl.AclExpression.Exists;
import io.taskmigo.acl.AclExpression.Ref;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/// Holds immutable built-in rules and a write-through POC registry of already-compiled custom policies.
///
/// Custom policy persistence and cross-instance invalidation are deliberately outside this POC. Resource rows are never
/// cached here; response predicates are still pushed to the database by the owning resource module.
@Service
public final class AclPolicyRegistry {

    private static final Set<String> API_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private final AclPolicyDefinitionCompiler compiler = new AclPolicyDefinitionCompiler();
    private final List<RequestAclPolicy> systemRequests = List.of(
        new RequestAclPolicy(
            "system/api-authenticated",
            RequestAclPolicy.Origin.SYSTEM,
            new ApiTarget(API_METHODS, "/api/v0/**"),
            List.of(
                new RequestAclPolicy.Rule(
                    "authenticated-principal",
                    RequestAclPolicy.Effect.ALLOW,
                    new Exists(new Ref("principal.id"))
                )
            )
        )
    );
    private final List<ResponseAclPolicy> systemResponses = List.of(
        new ResponseAclPolicy(
            "system/project-organization-boundary",
            ResponseAclPolicy.Origin.SYSTEM,
            new ApiTarget(Set.of("GET"), "/api/v0/projects"),
            List.of(
                new ResponseAclPolicy.Rule(
                    "same-organization",
                    ResponseAclPolicy.Effect.ALLOW,
                    new Eq(new Ref("object.organizationId"), new Ref("principal.organizationId")),
                    ResponseAclPolicy.FieldSelection.allFields()
                )
            )
        )
    );

    private final Map<UUID, Map<String, RequestAclPolicy>> customRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, ResponseAclPolicy>> customResponses = new ConcurrentHashMap<>();

    public void upsertCustom(UUID organizationId, String name, Map<String, Object> definition) {
        switch (this.compiler.kind(definition)) {
            case "acl/request" -> {
                RequestAclPolicy policy = this.compiler.compileRequest(name, RequestAclPolicy.Origin.CUSTOM, definition);
                this.customResponses.computeIfPresent(organizationId, (ignored, policies) -> without(policies, name));
                this.customRequests.compute(organizationId, (ignored, policies) -> with(policies, name, policy));
            }
            case "acl/response" -> {
                ResponseAclPolicy policy = this.compiler.compileResponse(name, ResponseAclPolicy.Origin.CUSTOM, definition);
                this.customRequests.computeIfPresent(organizationId, (ignored, policies) -> without(policies, name));
                this.customResponses.compute(organizationId, (ignored, policies) -> with(policies, name, policy));
            }
            default -> throw new IllegalArgumentException("ACL kind must be acl/request or acl/response");
        }
    }

    public void deleteCustom(UUID organizationId, String name) {
        this.customRequests.computeIfPresent(organizationId, (ignored, policies) -> without(policies, name));
        this.customResponses.computeIfPresent(organizationId, (ignored, policies) -> without(policies, name));
    }

    public List<String> customPolicyNames(UUID organizationId) {
        var names = new java.util.TreeSet<String>();
        names.addAll(this.customRequests.getOrDefault(organizationId, Map.of()).keySet());
        names.addAll(this.customResponses.getOrDefault(organizationId, Map.of()).keySet());
        return List.copyOf(names);
    }

    public List<RequestAclPolicy> requestPolicies(@Nullable UUID organizationId) {
        List<RequestAclPolicy> policies = new ArrayList<>(this.systemRequests);
        if (organizationId != null) policies.addAll(this.customRequests.getOrDefault(organizationId, Map.of()).values());
        return List.copyOf(policies);
    }

    public List<ResponseAclPolicy> responsePolicies(@Nullable UUID organizationId) {
        List<ResponseAclPolicy> policies = new ArrayList<>(this.systemResponses);
        if (organizationId != null) policies.addAll(this.customResponses.getOrDefault(organizationId, Map.of()).values());
        return List.copyOf(policies);
    }

    private static <T> Map<String, T> with(@Nullable Map<String, T> current, String name, T policy) {
        Map<String, T> copy = current == null ? new LinkedHashMap<>() : new LinkedHashMap<>(current);
        copy.put(name, policy);
        return Map.copyOf(copy);
    }

    private static <T> Map<String, T> without(Map<String, T> current, String name) {
        Map<String, T> copy = new LinkedHashMap<>(current);
        copy.remove(name);
        return Map.copyOf(copy);
    }
}
