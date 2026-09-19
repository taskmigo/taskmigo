package io.taskmigo.migration;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.yaml.YAMLMapper;

/// Loads the flat declarative resources used by the one-shot migration application.
@Component
final class MigrationResourceLoader {

    private static final String RESOURCE_PREFIX = "migration/";

    private final Environment environment;
    private final YAMLMapper yaml = YAMLMapper.builder().build();
    private final YamlPropertySourceLoader propertySourceLoader = new YamlPropertySourceLoader();

    MigrationResourceLoader(Environment environment) {
        this.environment = environment;
    }

    MigrationResources load() {
        try {
            List<User> users = this.readList("users.yaml", new TypeReference<List<User>>() {})
                .stream()
                .map(this::resolveUser)
                .toList();
            return new MigrationResources(
                users,
                this.readList("roles.yaml", new TypeReference<>() {}),
                this.readList("statements.yaml", new TypeReference<>() {}),
                this.readList("groups.yaml", new TypeReference<>() {}),
                this.readClients()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load migration resources", exception);
        }
    }

    private <T> List<T> readList(String filename, TypeReference<List<T>> type) throws IOException {
        return List.copyOf(this.yaml.readValue(this.read(filename), type));
    }

    private User resolveUser(User user) {
        String password =
            user.password() == null ? null : this.environment.resolveRequiredPlaceholders(user.password());
        return new User(
            user.username(),
            password,
            user.emails(),
            user.firstName(),
            user.lastName(),
            user.roles(),
            user.groups(),
            user.absent()
        );
    }

    private Map<String, Client> readClients() throws IOException {
        if (!this.environment.getProperty("TM_BROWSER_AUTHENTICATION_ENABLED", Boolean.class, true)) {
            return Map.of();
        }
        byte[] source = this.read("security.yaml");
        Map<String, Object> root = this.yaml.readValue(source, new TypeReference<>() {});
        if (!root.keySet().equals(Set.of("clients"))) {
            throw new IllegalStateException("security.yaml must contain only the clients root property");
        }

        Resource resource = new ByteArrayResource(source, "security.yaml");
        List<PropertySource<?>> sources = this.propertySourceLoader.load("migration-security", resource);
        Binder binder = new Binder(
            ConfigurationPropertySources.from(sources),
            new PropertySourcesPlaceholdersResolver(this.environment)
        );
        return Map.copyOf(
            binder
                .bind(
                    "clients",
                    Bindable.mapOf(String.class, Client.class),
                    new NoUnboundElementsBindHandler(BindHandler.DEFAULT)
                )
                .orElseGet(Map::of)
        );
    }

    private byte[] read(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PREFIX + filename);
        try (var input = resource.getInputStream()) {
            return input.readAllBytes();
        }
    }

    record MigrationResources(
        List<User> users,
        List<Role> roles,
        List<Statement> statements,
        List<Group> groups,
        Map<String, Client> clients
    ) {}

    record User(
        String username,
        @Nullable String password,
        List<String> emails,
        String firstName,
        String lastName,
        List<String> roles,
        List<String> groups,
        boolean absent
    ) {
        User {
            emails = values(emails);
            roles = values(roles);
            groups = values(groups);
        }
    }

    record Role(
        String code,
        String displayName,
        @Nullable String description,
        List<String> statements,
        boolean absent
    ) {
        Role {
            statements = values(statements);
        }
    }

    record Statement(
        String code,
        @Nullable String description,
        String effect,
        String scope,
        Target target,
        String policy,
        boolean absent
    ) {}

    record Target(Api api) {}

    record Api(String method, String path) {}

    record Group(String code, String displayName, @Nullable String description, List<String> roles, boolean absent) {
        Group {
            roles = values(roles);
        }
    }

    private static <T> List<T> values(@Nullable List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
