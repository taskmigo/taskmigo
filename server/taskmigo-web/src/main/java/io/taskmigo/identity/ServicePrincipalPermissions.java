package io.taskmigo.identity;

import java.util.Set;

public final class ServicePrincipalPermissions {

    public static final String SYSTEM_RESOURCES_MANAGE = "system.resources.manage";
    static final Set<String> ALL = Set.of(SYSTEM_RESOURCES_MANAGE);

    private ServicePrincipalPermissions() {}
}
