package io.taskmigo.auth.authorization.statement;

/// Describes the authorization target category and HTTP API selector of a Statement.
public record TargetInfo(TargetType type, ApiInfo api) {}
