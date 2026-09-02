package io.taskmigo.auth.authorization.statement;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Exposes a persisted authorization Statement to authorization evaluators and application consumers.
public record StatementInfo(
    UUID id,
    String name,
    @Nullable String description,
    Effect effect,
    TargetInfo target,
    List<String> conditions
) {
    /// Tests an incoming method and path using the target method and full-match path expression.
    public boolean matches(String requestMethod, String requestPath) {
        String pathWithoutQuery = requestPath.split("\\?", 2)[0];
        return (
            (this.target.api().method().equals("*") || this.target.api().method().equals(requestMethod)) &&
            Pattern.compile(this.target.api().path()).matcher(pathWithoutQuery).matches()
        );
    }
}
