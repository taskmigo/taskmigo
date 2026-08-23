package io.taskmigo.web.api.v0;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.web.api.v0.response.ApiResponse;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(
    properties = {
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-id=integration-client",
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-secret=integration-secret",
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-authentication-methods=client_secret_basic",
        "spring.security.oauth2.authorizationserver.client.cli.registration.authorization-grant-types=client_credentials",
        "spring.security.oauth2.authorizationserver.client.cli.registration.scopes=taskmigo.api",
        "taskmigo.security.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.security.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
class ApiV0ResponseContractTest {

    @Autowired
    RequestMappingHandlerMapping mappings;

    @Test
    void everyV0EndpointDeclaresTheStandardResponseEnvelope() {
        List<String> violations = new ArrayList<>();

        this.mappings.getHandlerMethods().forEach((mapping, handler) -> {
            boolean isV0 = mapping
                .getPatternValues()
                .stream()
                .anyMatch(pattern -> pattern.startsWith("/api/v0"));
            if (isV0 && !usesApiResponse(handler.getMethod().getGenericReturnType())) {
                violations.add(handler.getMethod().toGenericString());
            }
        });

        assertThat(violations)
            .as("Every /api/v0 endpoint must return ApiResponse so all v0 modules share one response contract")
            .isEmpty();
    }

    private static boolean usesApiResponse(Type returnType) {
        if (!(returnType instanceof ParameterizedType parameterizedType)) {
            return false;
        }
        if (parameterizedType.getRawType().equals(ApiResponse.class)) {
            return true;
        }
        if (!parameterizedType.getRawType().equals(ResponseEntity.class)) {
            return false;
        }
        Type bodyType = parameterizedType.getActualTypeArguments()[0];
        return bodyType instanceof ParameterizedType body && body.getRawType().equals(ApiResponse.class);
    }
}
