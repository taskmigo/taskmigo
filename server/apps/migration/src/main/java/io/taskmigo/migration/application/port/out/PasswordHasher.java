package io.taskmigo.migration.application.port.out;

/// Encodes and verifies installation-managed credentials without exposing a security framework.
public interface PasswordHasher {

    /// Encodes a raw credential.
    ///
    /// @param raw raw credential
    /// @return encoded credential
    String hash(String raw);

    /// Checks whether a raw credential matches an encoded value.
    ///
    /// @param raw raw credential
    /// @param encoded encoded credential
    /// @return whether the values match
    boolean matches(String raw, String encoded);
}
