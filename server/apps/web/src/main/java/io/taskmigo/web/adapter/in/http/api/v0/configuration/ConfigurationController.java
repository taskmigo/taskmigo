package io.taskmigo.web.adapter.in.http.api.v0.configuration;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.identity.configuration.ConfigurationSnapshot;
import io.taskmigo.identity.configuration.RetentionDuration;
import io.taskmigo.identity.configuration.application.port.in.api.ConfigurationService;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiResponse;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiResponseFactory;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiV0Responses.OpenApiCommonErrors;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiV0Responses.OpenApiUnsupportedMediaType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(version = "0", produces = MediaType.APPLICATION_JSON_VALUE)
@OpenApiCommonErrors
@Tag(name = "Configuration")
class ConfigurationController {

    private final ConfigurationService configuration;
    private final ApiResponseFactory responses;

    ConfigurationController(ConfigurationService configuration, ApiResponseFactory responses) {
        this.configuration = configuration;
        this.responses = responses;
    }

    @GetMapping("/configuration")
    @Operation(summary = "Get application configuration")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<Response, ApiResponse.BasicMeta>> get() {
        return this.responses.ok(
            Response.from(this.configuration.get()),
            "resource.configuration.read",
            "Configuration read"
        );
    }

    @PatchMapping(value = "/configuration", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update application configuration")
    @OpenApiUnsupportedMediaType
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<Response, ApiResponse.BasicMeta>> update(@RequestBody PatchRequest request) {
        request.requireKnownProperties();
        RetentionPatch retention = request.retention();
        ConfigurationSnapshot snapshot;
        if (retention == null || retention.user() == null) {
            snapshot = this.configuration.get();
        } else {
            retention.requireKnownProperties();
            try {
                snapshot = this.configuration.updateUserRetention(RetentionDuration.parse(retention.user()));
            } catch (IllegalArgumentException exception) {
                throw new UserException(UserException.Type.INVALID_INPUT, "Invalid retention duration", exception);
            }
        }
        return this.responses.ok(Response.from(snapshot), "resource.configuration.updated", "Configuration updated");
    }

    @Schema(name = "ApplicationConfiguration")
    record Response(RetentionResponse retention) {
        static Response from(ConfigurationSnapshot configuration) {
            return new Response(new RetentionResponse(configuration.retention().user().toString()));
        }
    }

    @Schema(name = "RetentionConfiguration")
    record RetentionResponse(String user) {}

    @Schema(name = "PatchApplicationConfiguration")
    static final class PatchRequest {

        private @Nullable RetentionPatch retention;
        private final Map<String, Object> unknown = new LinkedHashMap<>();

        public @Nullable RetentionPatch retention() {
            return this.retention;
        }

        public void setRetention(@Nullable RetentionPatch retention) {
            this.retention = retention;
        }

        @JsonAnySetter
        public void unknown(String name, Object value) {
            this.unknown.put(name, value);
        }

        void requireKnownProperties() {
            if (!this.unknown.isEmpty()) {
                throw new UserException(
                    UserException.Type.INVALID_INPUT,
                    "Unknown configuration properties: " + String.join(", ", this.unknown.keySet())
                );
            }
        }
    }

    @Schema(name = "PatchRetentionConfiguration")
    static final class RetentionPatch {

        private @Nullable String user;
        private final Map<String, Object> unknown = new LinkedHashMap<>();

        public @Nullable String user() {
            return this.user;
        }

        public void setUser(@Nullable String user) {
            this.user = user;
        }

        @JsonAnySetter
        public void unknown(String name, Object value) {
            this.unknown.put(name, value);
        }

        void requireKnownProperties() {
            if (!this.unknown.isEmpty()) {
                throw new UserException(
                    UserException.Type.INVALID_INPUT,
                    "Unknown retention properties: " + String.join(", ", this.unknown.keySet())
                );
            }
        }
    }
}
