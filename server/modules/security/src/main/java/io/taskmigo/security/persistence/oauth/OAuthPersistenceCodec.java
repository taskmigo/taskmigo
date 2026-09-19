package io.taskmigo.security.persistence.oauth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.jackson.OAuth2AuthorizationServerJacksonModule;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/// Serializes the flattened collection and JSON fields used by OAuth persistence.
@Component
final class OAuthPersistenceCodec {

    private final JsonMapper jsonMapper;

    OAuthPersistenceCodec() {
        this.jsonMapper = JsonMapper.builder()
            .addModules(
                SecurityJacksonModules.getModules(Objects.requireNonNull(OAuthPersistenceCodec.class.getClassLoader()))
            )
            .addModule(new OAuth2AuthorizationServerJacksonModule())
            .build();
    }

    Set<String> readDelimited(@Nullable String value) {
        return StringUtils.commaDelimitedListToSet(value);
    }

    String writeDelimited(Collection<String> values) {
        return StringUtils.collectionToCommaDelimitedString(values);
    }

    @Nullable
    String writeNullableDelimited(Collection<String> values) {
        return values.isEmpty() ? null : this.writeDelimited(values);
    }

    Map<String, Object> readMap(@Nullable String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyMap();
        }
        try {
            JavaType type = this.jsonMapper
                .getTypeFactory()
                .constructType(new ParameterizedTypeReference<Map<String, Object>>() {}.getType());
            return this.jsonMapper.readValue(value, type);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid OAuth JSON value", exception);
        }
    }

    String writeMap(Map<String, Object> value) {
        try {
            return this.jsonMapper.writeValueAsString(this.normalizeMap(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unable to serialize OAuth JSON value", exception);
        }
    }

    AuthorizationGrantType authorizationGrantType(String value) {
        return new AuthorizationGrantType(value);
    }

    ClientAuthenticationMethod clientAuthenticationMethod(String value) {
        return new ClientAuthenticationMethod(value);
    }

    @SuppressWarnings("NullAway")
    private Map<String, Object> normalizeMap(Map<String, Object> value) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        value.forEach((key, item) -> normalized.put(key, this.normalize(item)));
        return normalized;
    }

    @SuppressWarnings("NullAway")
    private @Nullable Object normalize(@Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, item) -> normalized.put(String.valueOf(key), this.normalize(item)));
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            ArrayList<Object> normalized = new ArrayList<>(collection.size());
            collection.forEach(item -> normalized.add(this.normalize(item)));
            return normalized;
        }
        return value;
    }
}
