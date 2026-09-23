package io.taskmigo.migration.application.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Describes one complete desired installation state independently from transport and persistence frameworks.
public record InstallationPlan(
    List<User> users,
    List<Role> roles,
    List<Statement> statements,
    List<Group> groups,
    Map<String, OAuthClient> clients
) {
    public InstallationPlan {
        users = List.copyOf(users);
        roles = List.copyOf(roles);
        statements = List.copyOf(statements);
        groups = List.copyOf(groups);
        clients = Map.copyOf(clients);
    }

    /// Defines one managed Identity User.
    public record User(
        String username,
        @Nullable String password,
        List<String> emails,
        String firstName,
        String lastName,
        List<String> roles,
        List<String> groups,
        boolean absent
    ) {
        public User {
            emails = values(emails);
            roles = values(roles);
            groups = values(groups);
        }
    }

    /// Defines one managed Access Control Role.
    public record Role(
        String code,
        String displayName,
        @Nullable String description,
        List<String> statements,
        boolean absent
    ) {
        public Role {
            statements = values(statements);
        }
    }

    /// Defines one managed Access Control Statement.
    public record Statement(
        String code,
        @Nullable String description,
        String effect,
        String scope,
        Target target,
        String policy,
        boolean absent
    ) {}

    /// Defines one managed Statement target.
    public record Target(Api api) {}

    /// Defines one managed API target.
    public record Api(String method, String path) {}

    /// Defines one managed Identity Group.
    public record Group(
        String code,
        String displayName,
        @Nullable String description,
        List<String> roles,
        boolean absent
    ) {
        public Group {
            roles = values(roles);
        }
    }

    /// Defines one managed OAuth client using framework-neutral configuration values.
    public record OAuthClient(
        String clientId,
        String rawSecret,
        String clientName,
        Set<String> clientAuthenticationMethods,
        Set<String> authorizationGrantTypes,
        Set<String> redirectUris,
        Set<String> postLogoutRedirectUris,
        Set<String> scopes,
        boolean requireProofKey,
        boolean requireAuthorizationConsent,
        @Nullable String jwkSetUri,
        @Nullable String tokenEndpointAuthenticationSigningAlgorithm,
        Duration authorizationCodeTimeToLive,
        Duration accessTokenTimeToLive,
        String accessTokenFormat,
        Duration deviceCodeTimeToLive,
        boolean reuseRefreshTokens,
        Duration refreshTokenTimeToLive,
        String idTokenSignatureAlgorithm
    ) {
        public OAuthClient {
            clientAuthenticationMethods = Set.copyOf(clientAuthenticationMethods);
            authorizationGrantTypes = Set.copyOf(authorizationGrantTypes);
            redirectUris = Set.copyOf(redirectUris);
            postLogoutRedirectUris = Set.copyOf(postLogoutRedirectUris);
            scopes = Set.copyOf(scopes);
        }
    }

    private static <T> List<T> values(@Nullable List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
