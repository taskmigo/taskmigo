package io.taskmigo.resource;

import java.util.Set;

public final class PermissionCatalog {
    public static final String PROJECT_READ = "project.read";
    public static final String PROJECT_UPDATE = "project.update";
    public static final String PROJECT_MEMBERS_READ = "project.members.read";
    public static final String PROJECT_MEMBERS_MANAGE = "project.members.manage";

    public static final Set<String> ALL = Set.of(
        PROJECT_READ,
        PROJECT_UPDATE,
        PROJECT_MEMBERS_READ,
        PROJECT_MEMBERS_MANAGE
    );

    private PermissionCatalog() {}
}
