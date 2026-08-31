package io.taskmigo.acl;

import java.util.List;
import java.util.Set;

public record ResponseAclPolicy(String name, Origin origin, ApiTarget target, List<Rule> rules) {
    public ResponseAclPolicy {
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

    public record Rule(String name, Effect effect, AclExpression when, FieldSelection fields) {}

    public record FieldSelection(boolean all, Set<String> fields) {
        public FieldSelection {
            fields = Set.copyOf(fields);
        }

        public static FieldSelection allFields() {
            return new FieldSelection(true, Set.of());
        }

        public static FieldSelection only(Set<String> fields) {
            return new FieldSelection(false, fields);
        }

        public boolean allows(String field) {
            return this.all || this.fields.contains(field);
        }

        public FieldSelection intersect(FieldSelection other) {
            if (this.all) return other;
            if (other.all) return this;
            var intersection = new java.util.LinkedHashSet<>(this.fields);
            intersection.retainAll(other.fields);
            return only(intersection);
        }
    }
}
