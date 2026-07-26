package com.taskmigo.console.config;

import java.util.UUID;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

@Configuration
public class DatabaseBootstrapConfiguration {

    @Bean
    ApplicationRunner databaseBootstrap(JdbcOperations jdbc, PasswordEncoder passwordEncoder,
            RegisteredClientRepository clients, SecurityProperties properties) {
        return arguments -> {
            SecurityProperties.Bootstrap bootstrap = properties.bootstrap();
            jdbc.update("""
                    insert into app_user (username, password, enabled) values (?, ?, true)
                    on conflict (username) do update set password = excluded.password, enabled = true
                    """, bootstrap.username(), passwordEncoder.encode(bootstrap.password()));
            jdbc.update("""
                    insert into app_authority (username, authority) values (?, 'ROLE_USER'), (?, 'ROLE_ADMIN')
                    on conflict do nothing
                    """, bootstrap.username(), bootstrap.username());

            if (clients.findByClientId("taskmigo-browser") == null) {
                clients.save(browserClient());
            }
        };
    }

    private static RegisteredClient browserClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("taskmigo-browser")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1:8080/callback")
                .postLogoutRedirectUri("http://127.0.0.1:8080/")
                .scope("openid")
                .scope("profile")
                .scope("api.read")
                .scope("api.admin")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(true)
                        .build())
                .build();
    }
}
