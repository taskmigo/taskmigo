package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.statement.StatementInfo;
import java.util.List;
import java.util.UUID;

/// Resolves the effective authorization Statements for a principal.
public interface EffectiveStatementResolver {

    /// Returns the deduplicated Statements effective for the supplied user.
    ///
    /// Resource modules provide the persistence-backed implementation. The authorization core only depends on this
    /// snapshot boundary and does not know how users, groups, roles, or Statements are stored.
    ///
    /// @param userId the user whose effective authorization state is required
    /// @return every effective Statement exactly once
    List<StatementInfo> resolve(UUID userId);
}
