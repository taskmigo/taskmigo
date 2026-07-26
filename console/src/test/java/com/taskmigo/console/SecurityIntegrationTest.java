package com.taskmigo.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "app.security.bootstrap.password=test-password")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class SecurityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired MockMvc mvc;
    @Autowired RegisteredClientRepository clients;

    @Test
    void exposesOpenIdDiscoveryMetadata() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:9000"))
                .andExpect(jsonPath("$.authorization_endpoint").exists())
                .andExpect(jsonPath("$.token_endpoint").exists())
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"));
    }

    @Test
    void developmentClientUsesAuthorizationCodeAndRequiresPkce() throws Exception {
        var client = clients.findByClientId("taskmigo-browser");
        assertThat(client).isNotNull();
        assertThat(client.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(client.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();

    }

    @Test
    void apiRejectsMissingToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiAcceptsJwtWithRequiredScope() throws Exception {
        mvc.perform(get("/api/me").with(jwt()
                        .jwt(token -> token.subject("alice").claim("scope", List.of("api.read")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_api.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("alice"));
    }

    @Test
    void adminApiChecksAuthority() throws Exception {
        mvc.perform(get("/api/admin").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_api.read"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin").with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_api.admin"))))
                .andExpect(status().isOk());
    }
}
