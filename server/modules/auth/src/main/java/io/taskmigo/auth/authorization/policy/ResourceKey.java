package io.taskmigo.auth.authorization.policy;

/// Identifies a resource lookup independently of the policy property name that receives it.
public record ResourceKey(String type, String key) {}
