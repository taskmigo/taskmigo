package io.taskmigo.auth.authorization.object;

/// Names a logical operator accepted by an Object Authorization field.
public enum ObjectAuthorizationOperator {
    AND,
    OR,
    NOT,
    EQ,
    NE,
    GT,
    GE,
    LT,
    LE,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    MODULO,
    IN,
    ALL,
    ANY,
    NONE,
    LENGTH,
}
