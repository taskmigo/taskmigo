/// Configures OAuth and OpenID Connect authorization, Taskmigo-owned clients, interactive development login, and
/// signing keys.
///
/// Machine clients are reconciled from Spring Authorization Server configuration. The optional browser client is
/// reconciled from Taskmigo security configuration and uses Authorization Code with PKCE and rotating refresh tokens.
@org.springframework.modulith.ApplicationModule
@org.jspecify.annotations.NullMarked
package io.taskmigo.identity;
