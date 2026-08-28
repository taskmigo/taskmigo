package io.taskmigo.user;

import java.util.UUID;

/// Defines the immutable identity reserved for Taskmigo's platform bootstrap administrator.
///
/// The bootstrap user is global rather than organization-owned and receives complete system and project permission
/// catalogs instead of depending on tenant role assignments.
public final class SystemUser {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String USERNAME = "system";
    public static final String DISPLAY_NAME = "System";

    private SystemUser() {}
}
