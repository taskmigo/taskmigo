package io.taskmigo.authorization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class AuthorizationResource {

    private AuthorizationResource() {}

    public record Statement(
        String key,
        @Nullable String name,
        @Nullable String description,
        Match match,
        Target target,
        @Nullable Effect effect,
        @Nullable String when,
        @Nullable List<FieldRule> fields
    ) {
        public Statement {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public record Match(String method, String path) {}

    public record FieldRule(Effect effect, List<String> names, @Nullable String when) {
        public FieldRule {
            names = List.copyOf(names);
        }
    }

    public record Role(
        String key,
        @Nullable String name,
        @Nullable String description,
        @Nullable List<String> statements,
        @Nullable List<String> roles
    ) {
        public Role {
            statements = statements == null ? List.of() : List.copyOf(statements);
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record Group(
        String key,
        @Nullable String name,
        @Nullable String description,
        @Nullable List<String> statements,
        @Nullable List<String> groups
    ) {
        public Group {
            statements = statements == null ? List.of() : List.copyOf(statements);
            groups = groups == null ? List.of() : List.copyOf(groups);
        }
    }

    public enum Target {
        REQUEST,
        OBJECT;

        @JsonCreator
        public static Target fromWireValue(String value) {
            return Target.valueOf(value.toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String wireValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Effect {
        ALLOW,
        DENY;

        @JsonCreator
        public static Effect fromWireValue(String value) {
            return Effect.valueOf(value.toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String wireValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Origin {
        SYSTEM,
        CUSTOM,
    }
}
