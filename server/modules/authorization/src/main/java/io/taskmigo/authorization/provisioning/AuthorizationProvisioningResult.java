package io.taskmigo.authorization.provisioning;

/// Describes the provider-owned outcome of reconciling one managed authorization resource.
///
/// @param id the stable identifier of the reconciled resource
/// @param change the semantic state transition performed by provisioning
public record AuthorizationProvisioningResult<T>(T id, Change change) {

    /// Identifies the state transition produced by managed authorization provisioning.
    public enum Change {
        CREATED,
        UPDATED,
        UNCHANGED,
    }
}
