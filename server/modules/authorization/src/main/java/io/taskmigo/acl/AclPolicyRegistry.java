package io.taskmigo.acl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Loads global system ACL and organization-scoped custom ACL persisted in PostgreSQL.
@Service
public class AclPolicyRegistry {

    private static final String SYSTEM = "SYSTEM";
    private static final String CUSTOM = "CUSTOM";

    private final AclPolicyDefinitionCompiler compiler = new AclPolicyDefinitionCompiler();
    private final AclPolicyRepository repository;

    AclPolicyRegistry(AclPolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void upsertCustom(UUID organizationId, String name, Map<String, Object> definition) {
        String kind = this.compiler.kind(definition);
        this.validate(name, CUSTOM, kind, definition);
        AclPolicyEntity entity = this.repository
            .findByOriginAndOrganizationIdAndName(CUSTOM, organizationId, name)
            .orElseGet(() -> new AclPolicyEntity(UUID.randomUUID(), CUSTOM, organizationId, name, kind, definition));
        entity.replace(kind, definition);
        this.repository.save(entity);
    }

    @Transactional
    public void deleteCustom(UUID organizationId, String name) {
        this.repository.deleteByOriginAndOrganizationIdAndName(CUSTOM, organizationId, name);
    }

    @Transactional(readOnly = true)
    public List<String> customPolicyNames(UUID organizationId) {
        return this.repository
            .findAllByOriginAndOrganizationIdOrderByName(CUSTOM, organizationId)
            .stream()
            .map(policy -> policy.name)
            .toList();
    }

    /// Reconciles the complete desired set of immutable system policies supplied by installation bootstrap.
    @Transactional
    public void reconcileSystem(Map<String, Map<String, Object>> definitions) {
        for (var entry : definitions.entrySet()) {
            String kind = this.compiler.kind(entry.getValue());
            this.validate(entry.getKey(), SYSTEM, kind, entry.getValue());
        }

        Map<String, AclPolicyEntity> stale = new LinkedHashMap<>();
        for (AclPolicyEntity entity : this.repository.findAllByOriginOrderByName(SYSTEM))
            stale.put(entity.name, entity);

        List<AclPolicyEntity> desired = new ArrayList<>();
        for (var entry : definitions.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> definition = entry.getValue();
            String kind = this.compiler.kind(definition);
            AclPolicyEntity entity = stale.remove(name);
            if (entity == null) entity = new AclPolicyEntity(UUID.randomUUID(), SYSTEM, null, name, kind, definition);
            entity.replace(kind, definition);
            desired.add(entity);
        }

        this.repository.saveAll(desired);
        this.repository.deleteAll(stale.values());
    }

    @Transactional(readOnly = true)
    public PolicySnapshot snapshot(@Nullable UUID organizationId) {
        List<RequestAclPolicy> requests = new ArrayList<>();
        List<ResponseAclPolicy> responses = new ArrayList<>();

        for (AclPolicyEntity entity : this.repository.findAllByOriginOrderByName(SYSTEM)) {
            this.compile(entity, RequestAclPolicy.Origin.SYSTEM, ResponseAclPolicy.Origin.SYSTEM, requests, responses);
        }
        if (organizationId != null) {
            for (AclPolicyEntity entity : this.repository.findAllByOriginAndOrganizationIdOrderByName(
                CUSTOM,
                organizationId
            )) {
                this.compile(
                    entity,
                    RequestAclPolicy.Origin.CUSTOM,
                    ResponseAclPolicy.Origin.CUSTOM,
                    requests,
                    responses
                );
            }
        }
        return new PolicySnapshot(List.copyOf(requests), List.copyOf(responses));
    }

    private void compile(
        AclPolicyEntity entity,
        RequestAclPolicy.Origin requestOrigin,
        ResponseAclPolicy.Origin responseOrigin,
        List<RequestAclPolicy> requests,
        List<ResponseAclPolicy> responses
    ) {
        switch (entity.kind) {
            case "acl/request" -> requests.add(
                this.compiler.compileRequest(entity.name, requestOrigin, entity.definition)
            );
            case "acl/response" -> responses.add(
                this.compiler.compileResponse(entity.name, responseOrigin, entity.definition)
            );
            default -> throw new IllegalStateException("Unsupported persisted ACL kind: " + entity.kind);
        }
    }

    private void validate(String name, String origin, String kind, Map<String, Object> definition) {
        switch (kind) {
            case "acl/request" -> this.compiler.compileRequest(
                name,
                SYSTEM.equals(origin) ? RequestAclPolicy.Origin.SYSTEM : RequestAclPolicy.Origin.CUSTOM,
                definition
            );
            case "acl/response" -> this.compiler.compileResponse(
                name,
                SYSTEM.equals(origin) ? ResponseAclPolicy.Origin.SYSTEM : ResponseAclPolicy.Origin.CUSTOM,
                definition
            );
            default -> throw new IllegalArgumentException("ACL kind must be acl/request or acl/response");
        }
    }

    public record PolicySnapshot(List<RequestAclPolicy> requestPolicies, List<ResponseAclPolicy> responsePolicies) {}
}
