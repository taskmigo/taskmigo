package io.taskmigo.embeddedlanguage;

/// Validates query-dependent Embedded Language operations without knowing persistence technology.
@SuppressWarnings("checkstyle:NeedBraces")
public final class LanguageQueryability {

    private LanguageQueryability() {}

    /// Validates every query-relevant reference and operator in a compiled program.
    public static void validate(LanguageIr program, QueryCapability capability) {
        validate(program.expression(), capability);
    }

    private static void validate(LanguageIr.Expression expression, QueryCapability capability) {
        switch (expression) {
            case LanguageIr.Literal _ -> {
            }
            case LanguageIr.Reference reference -> {
                if (capability.roots().contains(reference.root())) {
                    String path = String.join(".", reference.path());
                    if (!capability.paths().contains(path)) {
                        throw failure(
                            "program reference is not queryable: " + reference.root() + "." + path,
                            reference.span()
                        );
                    }
                }
            }
            case LanguageIr.ListLiteral list -> list.values().forEach(value -> validate(value, capability));
            case LanguageIr.Binary binary -> {
                if (!capability.binaryOperators().contains(binary.operator())) throw failure(
                    "program operator is not queryable: " + binary.operator(),
                    binary.span()
                );
                validate(binary.left(), capability);
                validate(binary.right(), capability);
            }
            case LanguageIr.Unary unary -> {
                if (!capability.unaryOperators().contains(unary.operator())) throw failure(
                    "program operator is not queryable: " + unary.operator(),
                    unary.span()
                );
                validate(unary.operand(), capability);
            }
            case LanguageIr.Conditional conditional -> {
                validate(conditional.condition(), capability);
                validate(conditional.whenTrue(), capability);
                validate(conditional.whenFalse(), capability);
            }
        }
    }

    private static EmbeddedLanguageException failure(String message, LanguageDiagnostic.SourceSpan span) {
        return new EmbeddedLanguageException(
            new LanguageDiagnostic(LanguageDiagnostic.Category.QueryabilityError, message, span)
        );
    }
}
