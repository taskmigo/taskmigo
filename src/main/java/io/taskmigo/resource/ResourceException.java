package io.taskmigo.resource;

import org.springframework.http.HttpStatus;

public final class ResourceException extends RuntimeException {
    private final HttpStatus status;

    ResourceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    ResourceException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
