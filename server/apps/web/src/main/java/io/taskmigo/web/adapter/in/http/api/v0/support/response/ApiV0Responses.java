package io.taskmigo.web.adapter.in.http.api.v0.support.response;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Reusable OpenAPI response annotations for the version 0 HTTP API.
public final class ApiV0Responses {

    private ApiV0Responses() {}

    /// Documents responses that every current version 0 operation can return.
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(ref = "#/components/schemas/ApiResponseVoidBasicMeta")
            )
        ),
        @ApiResponse(responseCode = "406", description = "Not Acceptable", content = @Content),
        @ApiResponse(
            responseCode = "422",
            description = "Unprocessable Content",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(ref = "#/components/schemas/ApiResponseVoidBasicMeta")
            )
        ),
    })
    public @interface CommonOpenApiErrorResponses {}

    /// Documents rejection of a request body whose media type is not JSON-compatible.
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "415", description = "Unsupported Media Type", content = @Content)
    public @interface UnsupportedMediaTypeOpenApiResponse {}

    /// Documents an operation-specific missing-resource response.
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(
        responseCode = "404",
        description = "Not Found",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(ref = "#/components/schemas/ApiResponseVoidBasicMeta")
        )
    )
    public @interface NotFoundOpenApiResponse {}

    /// Documents an operation-specific resource-state conflict.
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(
        responseCode = "409",
        description = "Conflict",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(ref = "#/components/schemas/ApiResponseVoidBasicMeta")
        )
    )
    public @interface ConflictOpenApiResponse {}
}
