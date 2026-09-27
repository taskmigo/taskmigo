package io.taskmigo.authorization.statement;

/// Describes the HTTP method and path expression selected by a Statement.
///
/// The path is interpreted at authorization runtime using full-match, RE2-compatible bounded regular-expression
/// semantics against the request path without its query string. Unsupported syntax fails closed when the Statement
/// participates in authorization.
public record ApiInfo(String method, String path) {}
