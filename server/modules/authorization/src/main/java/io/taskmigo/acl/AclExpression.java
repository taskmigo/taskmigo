package io.taskmigo.acl;

import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface AclExpression
    permits AclExpression.Eq, AclExpression.Exists, AclExpression.All, AclExpression.Any, AclExpression.Not, AclExpression.Relation {

    record Eq(Value left, Value right) implements AclExpression {}

    record Exists(Value value) implements AclExpression {}

    record All(List<AclExpression> expressions) implements AclExpression {
        public All {
            expressions = List.copyOf(expressions);
        }
    }

    record Any(List<AclExpression> expressions) implements AclExpression {
        public Any {
            expressions = List.copyOf(expressions);
        }
    }

    record Not(AclExpression expression) implements AclExpression {}

    record Relation(String name, Value principal, Value object) implements AclExpression {}

    sealed interface Value permits Literal, Ref {}

    record Literal(@Nullable Object value) implements Value {}

    record Ref(String path) implements Value {}
}
