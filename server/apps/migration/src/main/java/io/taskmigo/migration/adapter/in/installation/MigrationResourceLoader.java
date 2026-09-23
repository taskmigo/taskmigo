package io.taskmigo.migration.adapter.in.installation;

import io.taskmigo.migration.application.model.InstallationPlan;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

/// Loads flat declarative installation resources and maps framework configuration into the application model.
@Component
final class MigrationResourceLoader {

    private static final String RESOURCE_PREFIX = "migration/";

    private final Environment environment;
    private final YAMLMapper yaml = YAMLMapper.builder().build();
    private final YamlPropertySourceLoader propertySourceLoader = new YamlPropertySourceLoader();

    MigrationResourceLoader(Environment environment) {
        this.environment = environment;
    }

    InstallationPlan load() {
        try {
            List<InstallationPlan.User> users = this.readList(
                "users.yaml",
                new TypeReference<List<InstallationPlan.User>>() {}
            )
                .stream()
                .map(this::resolveUser)
                .toList();
            return new InstallationPlan(
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

    private InstallationPlan.User resolveUser(InstallationPlan.User user) {
        String password =
            user.password() == null ? null : this.environment.resolveRequiredPlaceholders(user.password());
        return new InstallationPlan.User(
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

    private Map<String, InstallationPlan.OAuthClient> readClients() throws IOException {
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
        Map<String, Client> clients = binder
            .bind(
                "clients",
                Bindable.mapOf(String.class, Client.class),
                new NoUnboundElementsBindHandler(BindHandler.DEFAULT)
            )
            .orElseGet(Map::of);
        return oauthClients(clients);
    }

    static Map<String, InstallationPlan.OAuthClient> oauthClients(Map<String, Client> clients) {
        Map<String, InstallationPlan.OAuthClient> result = new LinkedHashMap<>();
        clients.forEach((registrationId, client) -> result.put(registrationId, oauthClient(client)));
        return Map.copyOf(result);
    }

    private static InstallationPlan.OAuthClient oauthClient(Client client) {
        var registration = client.getRegistration();
        var token = client.getToken();
        String clientId = Objects.requireNonNull(registration.getClientId(), "Client id is required");
        if (registration.getClientAuthenticationMethods().isEmpty()) {
            throw new IllegalStateException("Client authentication methods are required: " + clientId);
        }
        if (registration.getAuthorizationGrantTypes().isEmpty()) {
            throw new IllegalStateException("Authorization grant types are required: " + clientId);
        }
        return new InstallationPlan.OAuthClient(
            clientId,
            Objects.requireNonNull(registration.getClientSecret(), "Client secret is required"),
            registration.getClientName() == null ? clientId : registration.getClientName(),
            registration.getClientAuthenticationMethods(),
            registration.getAuthorizationGrantTypes(),
            registration.getRedirectUris(),
            registration.getPostLogoutRedirectUris(),
            registration.getScopes(),
            client.isRequireProofKey(),
            client.isRequireAuthorizationConsent(),
            client.getJwkSetUri(),
            client.getTokenEndpointAuthenticationSigningAlgorithm(),
            Objects.requireNonNull(token.getAuthorizationCodeTimeToLive()),
            Objects.requireNonNull(token.getAccessTokenTimeToLive()),
            Objects.requireNonNull(token.getAccessTokenFormat()),
            Objects.requireNonNull(token.getDeviceCodeTimeToLive()),
            token.isReuseRefreshTokens(),
            Objects.requireNonNull(token.getRefreshTokenTimeToLive()),
            Objects.requireNonNull(token.getIdTokenSignatureAlgorithm(), "ID token signature algorithm is required")
        );
    }

    private byte[] read(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PREFIX + filename);
        try (var input = resource.getInputStream()) {
            return input.readAllBytes();
        }
    }
}
