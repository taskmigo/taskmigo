package io.taskmigo.bootstrap;

import io.taskmigo.acl.AclPolicyRegistry;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.yaml.YAMLMapper;

/// Reconciles classpath-owned system ACL YAML into PostgreSQL during installation bootstrap.
@Component
final class SystemAclPolicyReconciler implements ApplicationRunner {

    private static final String SYSTEM_ACL_PATTERN = "classpath*:system/acl/*.yaml";

    private final AclPolicyRegistry policies;
    private final YAMLMapper yaml = YAMLMapper.builder().build();

    SystemAclPolicyReconciler(AclPolicyRegistry policies) {
        this.policies = policies;
    }

    @Override
    public void run(ApplicationArguments arguments) throws IOException {
        this.reconcile();
    }

    void reconcile() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(SYSTEM_ACL_PATTERN);
        if (resources.length == 0) throw new IllegalStateException(
            "No system ACL policies found at " + SYSTEM_ACL_PATTERN
        );
        Arrays.sort(resources, java.util.Comparator.comparing(Resource::getDescription));

        Map<String, Map<String, Object>> definitions = new LinkedHashMap<>();
        for (Resource resource : resources) {
            Map<String, Object> definition = this.yaml.readValue(
                resource.getInputStream(),
                new TypeReference<Map<String, Object>>() {}
            );
            String name = policyName(definition, resource.getDescription());
            if (definitions.putIfAbsent(name, definition) != null) {
                throw new IllegalArgumentException("Duplicate system ACL policy name: " + name);
            }
        }
        this.policies.reconcileSystem(definitions);
    }

    private static String policyName(Map<String, Object> definition, String source) {
        Object rawMetadata = definition.get("metadata");
        if (!(rawMetadata instanceof Map<?, ?> metadata)) {
            throw new IllegalArgumentException("System ACL metadata is required: " + source);
        }
        Object rawName = metadata.get("name");
        if (!(rawName instanceof String name) || name.isBlank()) {
            throw new IllegalArgumentException("System ACL metadata.name must be a non-blank string: " + source);
        }
        return name.trim();
    }
}
