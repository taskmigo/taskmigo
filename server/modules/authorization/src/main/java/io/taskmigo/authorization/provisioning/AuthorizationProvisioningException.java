package io.taskmigo.authorization.provisioning;

import java.io.Serial;

/// Reports an invalid or incomplete managed authorization provisioning state.
public final class AuthorizationProvisioningException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthorizationProvisioningException(String message) {
        super(message);
    }
}
