package io.taskmigo.identity.provisioning;

/// Describes the provider-owned outcome of reconciling one managed Identity resource.
///
/// @param id the stable identifier of the reconciled resource
/// @param change the semantic state transition performed by provisioning
public record IdentityProvisioningResult<T>(T id, Change change) {
    /// Identifies the state transition produced by managed Identity provisioning.
    public enum Change {
        CREATED,
        UPDATED,
        UNCHANGED,
    }
}
