package io.taskmigo.embeddedlanguage;

import java.util.Set;

/// Describes the operations a program consumer can lower into its query language.
public record QueryCapability(
    Set<String> roots,
    Set<String> paths,
    Set<LanguageIr.BinaryOperator> binaryOperators,
    Set<LanguageIr.UnaryOperator> unaryOperators
) {
    /// Creates an immutable query capability.
    public QueryCapability {
        roots = Set.copyOf(roots);
        paths = Set.copyOf(paths);
        binaryOperators = Set.copyOf(binaryOperators);
        unaryOperators = Set.copyOf(unaryOperators);
    }
}
