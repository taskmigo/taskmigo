package io.taskmigo.bootstrap;

import io.taskmigo.authorization.AuthorizationResource.Group;
import io.taskmigo.authorization.AuthorizationResource.Origin;
import io.taskmigo.authorization.AuthorizationResource.Role;
import io.taskmigo.authorization.AuthorizationResource.Statement;
import io.taskmigo.authorization.AuthorizationResourceService;
import java.io.InputStream;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
class AuthorizationResourceReconciler implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationResourceReconciler.class);
    private static final ClassPathResource RESOURCE = new ClassPathResource("bootstrap/authorization/system.yaml");

    private final AuthorizationResourceService resources;
    private final YAMLMapper yaml = YAMLMapper.builder().build();

    AuthorizationResourceReconciler(AuthorizationResourceService resources) {
        this.resources = resources;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        Bundle bundle;
        try (InputStream input = RESOURCE.getInputStream()) {
            bundle = this.yaml.readValue(input, Bundle.class);
        }

        for (Statement statement : bundle.statements()) this.resources.upsertStatement(null, statement, Origin.SYSTEM);
        for (Role role : bundle.roles()) this.resources.upsertRole(null, role, Origin.SYSTEM);
        for (Group group : bundle.groups()) this.resources.upsertGroup(null, group, Origin.SYSTEM);
        LOGGER.info(
            "authorization_resources_reconciled statements={} roles={} groups={}",
            bundle.statements().size(),
            bundle.roles().size(),
            bundle.groups().size()
        );
    }

    private record Bundle(
        @Nullable List<Statement> statements,
        @Nullable List<Role> roles,
        @Nullable List<Group> groups
    ) {
        private Bundle {
            statements = statements == null ? List.of() : List.copyOf(statements);
            roles = roles == null ? List.of() : List.copyOf(roles);
            groups = groups == null ? List.of() : List.copyOf(groups);
        }
    }
}
