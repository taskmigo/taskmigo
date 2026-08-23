package io.taskmigo.identity;

import io.taskmigo.identity.IdentityProperties.InternalClientDefinition;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

final class InternalClientDefinitionValidator {

    private InternalClientDefinitionValidator() {}

    static void validate(String registrationKey, InternalClientDefinition definition) {
        if (!StringUtils.hasText(registrationKey)) {
            throw new IllegalStateException("Internal client key must not be blank");
        }
        if (!StringUtils.hasText(definition.clientSecret())) {
            throw new IllegalStateException(
                "client-secret is required for internal service client: " + registrationKey
            );
        }

        var unknownPermissions = definition
            .servicePermissions()
            .stream()
            .filter(Predicate.not(ServicePrincipalPermissions.ALL::contains))
            .collect(Collectors.toUnmodifiableSet());
        if (!unknownPermissions.isEmpty()) {
            throw new IllegalStateException(
                "Unknown service permissions for " + registrationKey + ": " + unknownPermissions
            );
        }
    }
}
