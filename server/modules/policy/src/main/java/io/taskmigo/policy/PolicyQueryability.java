package io.taskmigo.policy;

/// Validates query-dependent Policy Language operations without knowing persistence technology.
@SuppressWarnings("checkstyle:NeedBraces")
public final class PolicyQueryability {

    private PolicyQueryability() {}

    /// Validates every query-relevant reference and operator in a compiled policy.
    public static void validate(PolicyIr policy, QueryCapability capability) {
        validate(policy.expression(), capability);
    }

    private static void validate(PolicyIr.Expression expression, QueryCapability capability) {
        switch (expression) {
            case PolicyIr.Literal _ -> {
            }
            case PolicyIr.Reference reference -> {
                if (capability.roots().contains(reference.root())) {
                    String path = String.join(".", reference.path());
                    if (!capability.paths().contains(path)) {
                        throw failure(
                            "policy reference is not queryable: " + reference.root() + "." + path,
                            reference.span()
                        );
                    }
                }
            }
            case PolicyIr.ListLiteral list -> list.values().forEach(value -> validate(value, capability));
            case PolicyIr.Binary binary -> {
                if (!capability.binaryOperators().contains(binary.operator())) throw failure(
                    "policy operator is not queryable: " + binary.operator(),
                    binary.span()
                );
                validate(binary.left(), capability);
                validate(binary.right(), capability);
            }
            case PolicyIr.Unary unary -> {
                if (!capability.unaryOperators().contains(unary.operator())) throw failure(
                    "policy operator is not queryable: " + unary.operator(),
                    unary.span()
                );
                validate(unary.operand(), capability);
            }
            case PolicyIr.Conditional conditional -> {
                validate(conditional.condition(), capability);
                validate(conditional.whenTrue(), capability);
                validate(conditional.whenFalse(), capability);
            }
        }
    }

    private static PolicyException failure(String message, PolicyDiagnostic.SourceSpan span) {
        return new PolicyException(new PolicyDiagnostic(PolicyDiagnostic.Category.QueryabilityError, message, span));
    }
}
