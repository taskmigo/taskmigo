package io.taskmigo.web.security;

import java.util.Set;

final class ServicePrincipalPermissions {

    static final String SYSTEM_RESOURCES_MANAGE = "system.resources.manage";
    static final Set<String> ALL = Set.of(SYSTEM_RESOURCES_MANAGE);

    private ServicePrincipalPermissions() {}
}
