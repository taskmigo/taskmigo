package io.taskmigo.acl;

import java.util.List;

public record RequestAclPolicy(String name, Origin origin, ApiTarget target, List<Rule> rules) {
    public RequestAclPolicy {
        rules = List.copyOf(rules);
    }

    public enum Origin {
        SYSTEM,
        CUSTOM,
    }

    public enum Effect {
        ALLOW,
        DENY,
    }

    public record Rule(String name, Effect effect, AclExpression when) {}
}
