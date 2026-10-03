package io.taskmigo.identity.user.application.service;

/// Records which access/session categories were present when deletion lifecycle cleanup ran.
public record UserAccessRevocation(boolean roles, boolean statements, boolean groups, boolean sessions) {}
