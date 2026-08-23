package io.taskmigo.identity;

import java.util.Set;

/// Defines system-level permissions granted to Taskmigo-managed OAuth service principals.
///
/// These permission claims authorize machine-to-machine operations and are separate from project-role permissions in
/// the resource domain.
public final class ServicePrincipalPermissions {

    /// Authorizes system-level resource management and is required by the current versioned API security policy.
    public static final String SYSTEM_RESOURCES_MANAGE = "system.resources.manage";

    static final Set<String> ALL = Set.of(SYSTEM_RESOURCES_MANAGE);

    private ServicePrincipalPermissions() {}
}
