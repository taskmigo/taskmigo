package io.taskmigo.language.ast;

/// Stable binary operator view exposed to trusted Language translators.
public enum BinaryOperator {
    OR,
    AND,
    EQUAL,
    NOT_EQUAL,
    GREATER,
    GREATER_OR_EQUAL,
    LESS,
    LESS_OR_EQUAL,
    IN,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    MODULO,
}
