package io.taskmigo.auth.authorization.statement;

/// Describes the HTTP method and path expression selected by a Statement.
public record ApiInfo(String method, String path) {}
