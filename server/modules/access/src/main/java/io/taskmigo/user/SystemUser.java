package io.taskmigo.user;

/// Defines the reserved bootstrap username and default profile used to create Taskmigo's platform administrator.
///
/// The bootstrap account is persisted with the same schema as every other user. Its elevated permissions are an
/// authorization concern and are intentionally not represented by user-table flags or constraints.
public final class SystemUser {

    public static final String USERNAME = "system";
    public static final String FIRST_NAME = "System";
    public static final String LAST_NAME = "User";

    private SystemUser() {}
}
