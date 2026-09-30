package io.taskmigo.identity.user;

import java.util.UUID;

/// Identifies the authenticated principal responsible for a User mutation.
///
/// @param id stable principal identifier
/// @param username principal username captured from the authenticated token
public record UserMutationActor(UUID id, String username) {}
