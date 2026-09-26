package io.taskmigo.web.composition.bff;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaDelete;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// Stores the BFF's sealed OAuth session payload and serializes refresh-token rotation across application instances.
///
/// The browser-facing cookie remains the caller's local session snapshot. This coordinator is authoritative whenever
/// that snapshot enters its refresh window: exactly one caller owns the refresh lease, while concurrent callers wait
/// for the shared payload to advance instead of presenting the same rotating refresh token again.
@Component
public class BffSessionCoordinator {

    private static final long MAX_RETRY_AFTER_MILLISECONDS = 100;

    private final EntityManager entityManager;
    private final BffProperties properties;

    BffSessionCoordinator(EntityManager entityManager, BffProperties properties) {
        this.entityManager = entityManager;
        this.properties = properties;
    }

    /// Registers the initial sealed session state without overwriting a still-active session with the same identifier.
    ///
    /// @param sessionId stable identifier shared by every BFF pod handling the browser session
    /// @param payload encrypted and authenticated session payload opaque to the coordinator
    /// @param tokenExpiresAt access-token expiry as Unix epoch milliseconds
    /// @param expiresAt absolute browser-session expiry
    @Transactional
    public void ensure(UUID sessionId, String payload, long tokenExpiresAt, Instant expiresAt) {
        Instant now = Instant.now();
        BffSessionEntity existing = this.locked(sessionId);
        if (existing == null) {
            this.entityManager.persist(new BffSessionEntity(sessionId, payload, tokenExpiresAt, expiresAt));
            return;
        }
        if (!existing.expiresAt().isAfter(now)) {
            existing.reset(payload, tokenExpiresAt, expiresAt);
        }
    }

    /// Claims refresh ownership or returns the shared result produced by another owner.
    ///
    /// @param sessionId browser-session identifier
    /// @param owner unique identifier for the current refresh attempt
    /// @param refreshSkewMilliseconds time before access-token expiry at which a refresh is required
    /// @return the current distributed refresh state
    @Transactional
    public RefreshClaim claimRefresh(UUID sessionId, UUID owner, long refreshSkewMilliseconds) {
        Instant now = Instant.now();
        BffSessionEntity session = this.locked(sessionId);
        if (session == null || !session.expiresAt().isAfter(now)) {
            return new Missing();
        }

        long refreshThreshold = System.currentTimeMillis() + Math.max(0, refreshSkewMilliseconds);
        if (session.tokenExpiresAt() > refreshThreshold) {
            return new Ready(this.snapshot(session));
        }

        Instant leaseExpiresAt = session.refreshLeaseExpiresAt();
        UUID refreshOwner = session.refreshOwner();
        if (owner.equals(refreshOwner)) {
            return new Leader(this.snapshot(session));
        }
        if (refreshOwner != null && leaseExpiresAt != null && leaseExpiresAt.isAfter(now)) {
            long remaining = Math.max(1, leaseExpiresAt.toEpochMilli() - now.toEpochMilli());
            return new Wait(Math.min(MAX_RETRY_AFTER_MILLISECONDS, remaining));
        }

        session.claimRefresh(owner, now.plusMillis(this.properties.refreshLeaseMilliseconds()));
        return new Leader(this.snapshot(session));
    }

    /// Publishes a successfully refreshed session only when the caller still owns the distributed lease.
    ///
    /// @return true when the shared state was advanced; false when the lease was lost or the session disappeared
    @Transactional
    public boolean completeRefresh(UUID sessionId, UUID owner, String payload, long tokenExpiresAt) {
        BffSessionEntity session = this.locked(sessionId);
        if (session == null || !session.isRefreshOwnedBy(owner)) {
            return false;
        }
        session.completeRefresh(payload, tokenExpiresAt);
        return true;
    }

    /// Releases refresh ownership after a refresh attempt fails before shared state can advance.
    @Transactional
    public void releaseRefresh(UUID sessionId, UUID owner) {
        BffSessionEntity session = this.locked(sessionId);
        if (session != null && session.isRefreshOwnedBy(owner)) {
            session.releaseRefresh();
        }
    }

    /// Removes all replicated state for a browser session.
    @Transactional
    public void delete(UUID sessionId) {
        BffSessionEntity session = this.locked(sessionId);
        if (session != null) {
            this.entityManager.remove(session);
        }
    }

    @Scheduled(fixedDelayString = "${taskmigo.bff.session-cleanup-delay:PT15M}")
    @Transactional
    void deleteExpiredSessions() {
        var criteriaBuilder = this.entityManager.getCriteriaBuilder();
        CriteriaDelete<BffSessionEntity> delete = criteriaBuilder.createCriteriaDelete(BffSessionEntity.class);
        var root = delete.from(BffSessionEntity.class);
        delete.where(criteriaBuilder.lessThanOrEqualTo(root.get("expiresAt"), Instant.now()));
        this.entityManager.createQuery(delete).executeUpdate();
    }

    private @Nullable BffSessionEntity locked(UUID sessionId) {
        return this.entityManager.find(BffSessionEntity.class, sessionId, LockModeType.PESSIMISTIC_WRITE);
    }

    private Snapshot snapshot(BffSessionEntity session) {
        return new Snapshot(session.payload(), session.tokenExpiresAt(), session.generation());
    }

    /// Opaque shared session state returned to the BFF.
    public record Snapshot(String payload, long tokenExpiresAt, long generation) {}

    /// Result of an atomic refresh claim.
    public sealed interface RefreshClaim permits Ready, Leader, Wait, Missing {}

    /// Indicates that the shared access token is already fresh enough to use.
    public record Ready(Snapshot snapshot) implements RefreshClaim {}

    /// Indicates that the caller exclusively owns the current refresh attempt.
    public record Leader(Snapshot snapshot) implements RefreshClaim {}

    /// Indicates that another instance owns refresh and the caller should retry after the supplied delay.
    public record Wait(long retryAfterMilliseconds) implements RefreshClaim {}

    /// Indicates that the replicated session no longer exists or has expired.
    public record Missing() implements RefreshClaim {}
}
