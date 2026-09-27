package io.taskmigo.authorization.statement;

/// Describes the HTTP method and path expression selected by a Statement.
///
/// `path` uses full-match RE2-compatible regular-expression syntax after the request query string is removed.
/// Constructs that require backtracking semantics, including backreferences and look-around assertions, are rejected
/// when authorization processes the persisted target at runtime.
public record ApiInfo(String method, String path) {}
