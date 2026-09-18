package io.taskmigo.identity.provisioning;

import java.io.Serial;

/// Reports an invalid or incomplete managed Identity provisioning state.
public final class IdentityProvisioningException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IdentityProvisioningException(String message) {
        super(message);
    }
}
