package io.taskmigo.identity.user;

/// Describes the persisted lifecycle state of a User account.
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED,
    RETAINED,
    TOMBSTONE,
}
