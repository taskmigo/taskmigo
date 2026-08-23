package io.taskmigo.web.security;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskmigo.security")
record SecurityProperties(
    String issuer,
    Path signingKeyFile,
    String signingKeyId,
    Map<String, InternalClientProperties> internalClients
) {
    record InternalClientProperties(
        String clientId,
        @Nullable String clientSecret,
        boolean enabled,
        String clientType,
        Set<String> grantTypes,
        Set<String> redirectUris,
        Set<String> postLogoutRedirectUris,
        Set<String> scopes,
        Set<String> servicePermissions,
        boolean requireProofKey,
        boolean requireAuthorizationConsent
    ) {}
}
