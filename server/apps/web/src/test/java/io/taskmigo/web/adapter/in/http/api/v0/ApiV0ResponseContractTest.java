package io.taskmigo.web.adapter.in.http.api.v0;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiResponse;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(
    properties = {
        "taskmigo.oauth.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.oauth.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ApiV0ResponseContractTest {

    private final RequestMappingHandlerMapping mappings;

    ApiV0ResponseContractTest(RequestMappingHandlerMapping mappings) {
        this.mappings = mappings;
    }

    @Test
    @DisplayName("declares the standard response envelope for every v0 endpoint")
    void shouldDeclareStandardResponseEnvelopeWhenV0EndpointsAreInspected() {
        List<String> violations = new ArrayList<>();

        this.mappings.getHandlerMethods().forEach((mapping, handler) -> {
            RequestMapping controllerMapping = handler.getBeanType().getAnnotation(RequestMapping.class);
            boolean isV0 = controllerMapping != null && controllerMapping.version().equals("0");
            if (isV0) {
                boolean hasVersionedPrefix = mapping
                    .getPatternValues()
                    .stream()
                    .anyMatch(pattern -> pattern.startsWith("/api/v{version}"));
                if (!hasVersionedPrefix || !usesApiResponse(handler.getMethod().getGenericReturnType())) {
                    violations.add(handler.getMethod().toGenericString());
                }
            }
        });

        assertThat(violations)
            .as("Every /api/v0 endpoint must return ApiResponse so all v0 modules share one response contract")
            .isEmpty();
    }

    /**
     * Verifies that v0 request mappings expose one consistent JSON media-type contract.
     *
     * Given: every handler registered for API version 0.
     * Expect: every endpoint produces application/json, and every endpoint with a request body consumes
     * application/json.
     */
    @Test
    @DisplayName("declares JSON media types for every v0 endpoint")
    void shouldDeclareJsonMediaTypesWhenV0EndpointsAreInspected() {
        // Arrange
        List<String> violations = new ArrayList<>();

        // Act
        this.mappings.getHandlerMethods().forEach((mapping, handler) -> {
            RequestMapping controllerMapping = handler.getBeanType().getAnnotation(RequestMapping.class);
            boolean isV0 = controllerMapping != null && controllerMapping.version().equals("0");
            if (!isV0) {
                return;
            }

            boolean producesJsonOnly = mapping
                .getProducesCondition()
                .getProducibleMediaTypes()
                .equals(Set.of(MediaType.APPLICATION_JSON));
            boolean hasRequestBody = Arrays.stream(handler.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(RequestBody.class));
            boolean consumesJsonOnly =
                !hasRequestBody ||
                mapping.getConsumesCondition().getConsumableMediaTypes().equals(Set.of(MediaType.APPLICATION_JSON));

            if (!producesJsonOnly || !consumesJsonOnly) {
                violations.add(handler.getMethod().toGenericString());
            }
        });

        // Assert
        assertThat(violations)
            .as("Every /api/v0 endpoint must produce JSON, and endpoints with request bodies must consume JSON")
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
