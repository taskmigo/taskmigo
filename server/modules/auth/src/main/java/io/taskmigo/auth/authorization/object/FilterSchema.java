package io.taskmigo.auth.authorization.object;

import io.taskmigo.auth.authorization.filter.FilterAst;
import java.util.Map;
import java.util.Set;

/// Defines the safe persisted fields available to a resource's database filter.
public interface FilterSchema {
    /// Returns field names and their persisted Java types.
    Map<String, Class<?>> fields();

    /// Returns the Filter AST operators safe for this resource.
    default Set<FilterAst.Operator> operators() {
        return Set.of(
            FilterAst.Operator.AND,
            FilterAst.Operator.OR,
            FilterAst.Operator.NOT,
            FilterAst.Operator.EQ,
            FilterAst.Operator.NE,
            FilterAst.Operator.GT,
            FilterAst.Operator.GE,
            FilterAst.Operator.LT,
            FilterAst.Operator.LE,
            FilterAst.Operator.ADD,
            FilterAst.Operator.SUBTRACT,
            FilterAst.Operator.MULTIPLY,
            FilterAst.Operator.DIVIDE,
            FilterAst.Operator.NEGATE
        );
    }
}
