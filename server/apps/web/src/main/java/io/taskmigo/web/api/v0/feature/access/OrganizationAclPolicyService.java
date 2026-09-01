package io.taskmigo.web.api.v0.feature.access;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public final class OrganizationAclPolicyService {

    private final Map<UUID, Map<String, Policy>> policies = new ConcurrentHashMap<>();

    public void upsert(UUID organizationId, String name, Policy policy) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(policy, "policy");
        this.policies.computeIfAbsent(organizationId, ignored -> new ConcurrentHashMap<>()).put(name, policy);
    }

    public List<String> list(UUID organizationId) {
        return this.policies
            .getOrDefault(organizationId, Map.of())
            .keySet()
            .stream()
            .sorted(Comparator.naturalOrder())
            .toList();
    }

    public boolean delete(UUID organizationId, String name) {
        Map<String, Policy> organizationPolicies = this.policies.get(organizationId);
        if (organizationPolicies == null) return false;
        Policy removed = organizationPolicies.remove(name);
        if (organizationPolicies.isEmpty()) this.policies.remove(organizationId, organizationPolicies);
        return removed != null;
    }

    public record Policy(String kind, Map<String, Object> spec) {}
}
