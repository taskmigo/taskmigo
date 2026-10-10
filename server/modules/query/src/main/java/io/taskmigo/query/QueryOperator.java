package io.taskmigo.query;

/// Identifies logical operators a Query Schema may expose to clients or policy authors.
public enum QueryOperator {
    AND,
    OR,
    NOT,
    PLUS,
    MINUS,
    EQ,
    NE,
    GT,
    GE,
    GTE,
    LT,
    LE,
    LTE,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    MODULO,
    IN,
    CONTAINS,
    ALL,
    ANY,
    NONE,
    LENGTH,
}
