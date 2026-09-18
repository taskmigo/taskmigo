package io.taskmigo.security.oauth;

/// Defines the supported registered-client classifications.
public final class RegisteredClientType {

    public static final String INTERNAL = "internal";
    public static final String USER = "user";

    private RegisteredClientType() {}

    /// Validates and returns a registered-client classification.
    ///
    /// @param type the persisted classification
    /// @return the validated classification
    /// @throws IllegalArgumentException when the classification is unsupported
    public static String requireValid(String type) {
        if (!INTERNAL.equals(type) && !USER.equals(type)) {
            throw new IllegalArgumentException("Unsupported registered client type: " + type);
        }
        return type;
    }
}
