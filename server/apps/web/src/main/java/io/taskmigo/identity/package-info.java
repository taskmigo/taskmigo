/// Configures OAuth authorization and manages Taskmigo-owned service-principal clients and signing keys.
///
/// Internal clients are reconciled from Spring Authorization Server configuration at startup and receive managed
/// permission claims in client-credentials access tokens.
@org.springframework.modulith.ApplicationModule
@org.jspecify.annotations.NullMarked
package io.taskmigo.identity;
