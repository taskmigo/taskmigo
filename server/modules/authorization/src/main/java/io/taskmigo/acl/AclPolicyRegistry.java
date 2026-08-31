package io.taskmigo.acl;

import io.taskmigo.acl.AclExpression.Eq;
import io.taskmigo.acl.AclExpression.Exists;
import io.taskmigo.acl.AclExpression.Ref;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Loads immutable system ACL plus organization-scoped custom ACL persisted in PostgreSQL.
///
/// No policy cache is used in the POC: each HTTP request obtains a fresh database-backed snapshot and reuses that snapshot
/// for both request and response authorization. This keeps multi-instance behavior correct without Redis or another
/// coordination service.
@Service
public class AclPolicyRegistry {

    private static final Set<String> API_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private final AclPolicyDefinitionCompiler compiler = new AclPolicyDefinitionCompiler();
    private final AclPolicyRepository repository;
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

    AclPolicyRegistry(AclPolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void upsertCustom(UUID organizationId, String name, Map<String, Object> definition) {
        String kind = this.compiler.kind(definition);
        this.validate(name, kind, definition);
        AclPolicyEntity entity = this.repository
            .findByOrganizationIdAndName(organizationId, name)
            .orElseGet(() -> new AclPolicyEntity(UUID.randomUUID(), organizationId, name, kind, definition));
        entity.replace(kind, definition);
        this.repository.save(entity);
    }

    @Transactional
    public void deleteCustom(UUID organizationId, String name) {
        this.repository.deleteByOrganizationIdAndName(organizationId, name);
    }

    @Transactional(readOnly = true)
    public List<String> customPolicyNames(UUID organizationId) {
        return this.repository
            .findAllByOrganizationIdOrderByName(organizationId)
            .stream()
            .map(policy -> policy.name)
            .toList();
    }

    @Transactional(readOnly = true)
    public PolicySnapshot snapshot(@Nullable UUID organizationId) {
        List<RequestAclPolicy> requests = new ArrayList<>(this.systemRequests);
        List<ResponseAclPolicy> responses = new ArrayList<>(this.systemResponses);
        if (organizationId != null) {
            for (AclPolicyEntity entity : this.repository.findAllByOrganizationIdOrderByName(organizationId)) {
                switch (entity.kind) {
                    case "acl/request" -> requests.add(
                        this.compiler.compileRequest(entity.name, RequestAclPolicy.Origin.CUSTOM, entity.definition)
                    );
                    case "acl/response" -> responses.add(
                        this.compiler.compileResponse(entity.name, ResponseAclPolicy.Origin.CUSTOM, entity.definition)
                    );
                    default -> throw new IllegalStateException("Unsupported persisted ACL kind: " + entity.kind);
                }
            }
        }
        return new PolicySnapshot(List.copyOf(requests), List.copyOf(responses));
    }

    private void validate(String name, String kind, Map<String, Object> definition) {
        switch (kind) {
            case "acl/request" -> this.compiler.compileRequest(name, RequestAclPolicy.Origin.CUSTOM, definition);
            case "acl/response" -> this.compiler.compileResponse(name, ResponseAclPolicy.Origin.CUSTOM, definition);
            default -> throw new IllegalArgumentException("ACL kind must be acl/request or acl/response");
        }
    }

    public record PolicySnapshot(List<RequestAclPolicy> requestPolicies, List<ResponseAclPolicy> responsePolicies) {}
}
