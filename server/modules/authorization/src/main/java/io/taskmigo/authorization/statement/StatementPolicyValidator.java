package io.taskmigo.authorization.statement;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.core.AuthorizationName;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationCompilationProfile;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationEmbeddedLanguageSchemas;
import io.taskmigo.authorization.object.ObjectAuthorization;
import io.taskmigo.language.EmbeddedLanguageException;
import io.taskmigo.language.LanguageCompiler;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/// Validates authorization Statement definitions independently of persistence and transport adapters.
@Service
public final class StatementPolicyValidator {

    private static final Set<String> HTTP_METHODS = Set.of(
        "GET",
        "HEAD",
        "POST",
        "PUT",
        "PATCH",
        "DELETE",
        "OPTIONS",
        "TRACE"
    );

    private final ObjectAuthorization objectAuthorization;
    private final LanguageCompiler languageCompiler;

    /// Creates a validator with the authorization-owned policy compilers.
    public StatementPolicyValidator(ObjectAuthorization objectAuthorization, LanguageCompiler languageCompiler) {
        this.objectAuthorization = objectAuthorization;
        this.languageCompiler = languageCompiler;
    }

    /// Validates and normalizes a Statement definition for persistence.
    public StatementDefinition validate(
        @Nullable String name,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    ) {
        String validName = AuthorizationName.required(name, "name");
        Effect validEffect = required(effect, "effect");
        Scope validScope = required(scope, "scope");
        String validMethod = required(method, "target.api.method");
        if (!"*".equals(validMethod) && !HTTP_METHODS.contains(validMethod)) {
            throw new AuthorizationException("target.api.method must be a valid HTTP method");
        }
        if (validMethod.length() > 16) {
            throw new AuthorizationException("target.api.method must not exceed 16 characters");
        }
        String validPath = required(path, "target.api.path");
        if (validPath.length() > 2000) {
            throw new AuthorizationException("target.api.path must not exceed 2000 characters");
        }
        try {
            Pattern.compile(validPath);
        } catch (PatternSyntaxException exception) {
            throw new AuthorizationException("target.api.path must be a valid regular expression");
        }
        String validPolicy = requiredPolicy(policy);
        try {
            if (validScope == Scope.REQUEST) {
                this.languageCompiler.compile(
                    validPolicy,
                    AuthorizationEmbeddedLanguageSchemas.request(),
                    AuthorizationCompilationProfile.policy()
                );
            } else {
                this.objectAuthorization.validatePolicy(validPolicy, validMethod, validPath);
            }
        } catch (EmbeddedLanguageException exception) {
            throw new AuthorizationException("Invalid Statement policy: " + exception.getMessage());
        }
        return new StatementDefinition(
            validName,
            description,
            validEffect,
            validScope,
            validMethod,
            validPath,
            validPolicy
        );
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AuthorizationException(field + " must not be blank");
        }
        return value.trim();
    }

    private static <T> T required(@Nullable T value, String field) {
        if (value == null) {
            throw new AuthorizationException(field + " is required");
        }
        return value;
    }

    private static String requiredPolicy(@Nullable String policy) {
        if (policy == null || policy.isBlank()) {
            throw new AuthorizationException("policy must not be blank");
        }
        return policy;
    }
}
