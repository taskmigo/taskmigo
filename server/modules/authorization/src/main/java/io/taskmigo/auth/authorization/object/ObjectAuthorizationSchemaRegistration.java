package io.taskmigo.auth.authorization.object;

import java.util.Objects;
import java.util.regex.Pattern;

/// Associates an API route with the Object Authorization schema for its returned resource.
public record ObjectAuthorizationSchemaRegistration(String method, String path, ObjectAuthorizationSchema<?> schema) {
    public ObjectAuthorizationSchemaRegistration {
        Objects.requireNonNull(method);
        Objects.requireNonNull(path);
        Objects.requireNonNull(schema);
        if (method.isBlank() || path.isBlank()) {
            throw new IllegalArgumentException("schema route must not be blank");
        }
        Pattern.compile(path);
        method = method.equals("*") ? method : method.toUpperCase();
    }

    /// Returns whether this route is governed by a Statement target.
    public boolean matches(String statementMethod, String statementPath) {
        return (
            ("*".equals(statementMethod) || "*".equals(this.method) || this.method.equals(statementMethod)) &&
            Pattern.matches(statementPath, this.path)
        );
    }
}
