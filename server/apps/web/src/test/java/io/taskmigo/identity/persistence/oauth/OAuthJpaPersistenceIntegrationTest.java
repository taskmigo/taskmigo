package io.taskmigo.identity.persistence.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.PostgresTestConfiguration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.TestConstructor;

@SpringBootTest(
    properties = {
        "taskmigo.security.signing-key-file=build/test-data/oauth-jpa-signing-key.pem",
        "taskmigo.security.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OAuthJpaPersistenceIntegrationTest {

    private final RegisteredClientRepository clients;
    private final OAuth2AuthorizationService authorizations;
    private final OAuth2AuthorizationConsentService consents;

    OAuthJpaPersistenceIntegrationTest(
        RegisteredClientRepository clients,
        OAuth2AuthorizationService authorizations,
        OAuth2AuthorizationConsentService consents
    ) {
        this.clients = clients;
        this.authorizations = authorizations;
        this.consents = consents;
    }

    /**
     * Verifies that all SAS token variants and their metadata survive a JPA round-trip.
     *
     * Given: one registered client and an authorization containing state, authorization code, access token,
     * refresh token, OIDC ID token, user code, and device code.
     * Expect: the reloaded authorization contains the same token values, scopes, claims, and metadata, and every
     * supported token lookup returns that authorization.
     */
    @Test
    @DisplayName("round-trips authorization tokens and metadata through JPA")
    void shouldRoundTripAuthorizationTokensWhenAuthorizationIsSaved() {
        // Arrange
        RegisteredClient client = this.clients.findByClientId("internal__integration-client");
        assertThat(client).isNotNull();
        Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiresAt = Instant.parse("2026-01-01T01:00:00Z");
        Map<String, Object> claims = Map.of("sub", "system", "email", "system@example.com");
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
            .id("jpa-authorization-test")
            .principalName("system")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .authorizedScopes(Set.of("taskmigo.api"))
            .attribute(OAuth2ParameterNames.STATE, "jpa-state-test")
            .attribute("request_attribute", "value")
            .token(new OAuth2AuthorizationCode("jpa-code-test", issuedAt, expiresAt), metadata ->
                metadata.put("code_metadata", "value")
            )
            .token(
                new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    "jpa-access-test",
                    issuedAt,
                    expiresAt,
                    Set.of("taskmigo.api")
                ),
                metadata -> metadata.put("access_metadata", "value")
            )
            .token(new OAuth2RefreshToken("jpa-refresh-test", issuedAt, expiresAt), metadata ->
                metadata.put("refresh_metadata", "value")
            )
            .token(new OidcIdToken("jpa-id-token-test", issuedAt, expiresAt, claims), metadata -> {
                metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims);
                metadata.put("id_token_metadata", "value");
            })
            .token(new OAuth2UserCode("jpa-user-code-test", issuedAt, expiresAt), metadata ->
                metadata.put("user_code_metadata", "value")
            )
            .token(new OAuth2DeviceCode("jpa-device-code-test", issuedAt, expiresAt), metadata ->
                metadata.put("device_code_metadata", "value")
            )
            .build();

        // Act
        this.authorizations.save(authorization);
        OAuth2Authorization reloaded = this.authorizations.findById(authorization.getId());

        // Assert
        assertThat(reloaded).isNotNull();
        OAuth2Authorization.Token<OAuth2AuthorizationCode> code = Objects.requireNonNull(
            reloaded.getToken(OAuth2AuthorizationCode.class)
        );
        OAuth2Authorization.Token<OAuth2AccessToken> access = Objects.requireNonNull(
            reloaded.getToken(OAuth2AccessToken.class)
        );
        OAuth2Authorization.Token<OAuth2RefreshToken> refresh = Objects.requireNonNull(
            reloaded.getToken(OAuth2RefreshToken.class)
        );
        OAuth2Authorization.Token<OidcIdToken> idToken = Objects.requireNonNull(reloaded.getToken(OidcIdToken.class));
        OAuth2Authorization.Token<OAuth2UserCode> userCode = Objects.requireNonNull(
            reloaded.getToken(OAuth2UserCode.class)
        );
        OAuth2Authorization.Token<OAuth2DeviceCode> deviceCode = Objects.requireNonNull(
            reloaded.getToken(OAuth2DeviceCode.class)
        );
        assertThat(reloaded.<String>getAttribute(OAuth2ParameterNames.STATE)).isEqualTo("jpa-state-test");
        assertThat(reloaded.getAttributes()).containsEntry("request_attribute", "value");
        assertThat(code.getToken().getTokenValue()).isEqualTo("jpa-code-test");
        assertThat(code.getMetadata()).containsEntry("code_metadata", "value");
        assertThat(access.getToken().getTokenValue()).isEqualTo("jpa-access-test");
        assertThat(access.getToken().getScopes()).containsExactly("taskmigo.api");
        assertThat(refresh.getToken().getTokenValue()).isEqualTo("jpa-refresh-test");
        assertThat(idToken.getToken().getClaims()).containsEntry("email", "system@example.com");
        assertThat(userCode.getToken().getTokenValue()).isEqualTo("jpa-user-code-test");
        assertThat(deviceCode.getToken().getTokenValue()).isEqualTo("jpa-device-code-test");
        assertThat(this.authorizations.findByToken("jpa-state-test", null)).isNotNull();
        assertThat(
            this.authorizations.findByToken("jpa-code-test", new OAuth2TokenType(OAuth2ParameterNames.CODE))
        ).isNotNull();
        assertThat(
            this.authorizations.findByToken("jpa-access-test", new OAuth2TokenType(OAuth2ParameterNames.ACCESS_TOKEN))
        ).isNotNull();
        assertThat(
            this.authorizations.findByToken("jpa-refresh-test", new OAuth2TokenType(OAuth2ParameterNames.REFRESH_TOKEN))
        ).isNotNull();
        assertThat(
            this.authorizations.findByToken("jpa-id-token-test", new OAuth2TokenType(OidcParameterNames.ID_TOKEN))
        ).isNotNull();
        assertThat(
            this.authorizations.findByToken("jpa-user-code-test", new OAuth2TokenType(OAuth2ParameterNames.USER_CODE))
        ).isNotNull();
        assertThat(
            this.authorizations.findByToken(
                "jpa-device-code-test",
                new OAuth2TokenType(OAuth2ParameterNames.DEVICE_CODE)
            )
        ).isNotNull();

        this.authorizations.remove(authorization);
    }

    /**
     * Verifies that authorization consent uses its composite JPA key and retains granted authorities.
     *
     * Given: consent for the integration client and the system principal with one granted authority.
     * Expect: the consent can be loaded with the same pair of identifiers and is removed by the SAS service.
     */
    @Test
    @DisplayName("round-trips authorization consent through JPA")
    void shouldRoundTripConsentWhenConsentIsSaved() {
        // Arrange
        RegisteredClient client = this.clients.findByClientId("internal__integration-client");
        assertThat(client).isNotNull();
        OAuth2AuthorizationConsent consent = OAuth2AuthorizationConsent.withId(client.getId(), "system")
            .authority(new SimpleGrantedAuthority("SCOPE_taskmigo.api"))
            .build();

        // Act
        this.consents.save(consent);
        OAuth2AuthorizationConsent reloaded = this.consents.findById(client.getId(), "system");

        // Assert
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getAuthorities())
            .extracting(authority -> authority.getAuthority())
            .containsExactly("SCOPE_taskmigo.api");
        this.consents.remove(consent);
        assertThat(this.consents.findById(client.getId(), "system")).isNull();
    }
}
