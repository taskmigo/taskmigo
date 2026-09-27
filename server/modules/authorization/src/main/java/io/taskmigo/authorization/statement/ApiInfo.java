package io.taskmigo.authorization.statement;

/// Describes the HTTP method and path expression selected by a Statement.
///
/// `path` uses full-match semantics after the request query string is removed. Its expression is limited to Taskmigo's
/// bounded RE2-compatible subset: printable ASCII literals, '.', character classes, capturing groups, alternation,
/// anchors, '*', '+', '?', ASCII shorthand classes, and escaped punctuation. Engine extensions, Unicode/property
/// escapes, counted repetition, backreferences, look-around, and excessive grouping are rejected at authorization
/// runtime.
public record ApiInfo(String method, String path) {}
