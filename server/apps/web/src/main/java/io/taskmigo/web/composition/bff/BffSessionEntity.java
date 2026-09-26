package io.taskmigo.web.composition.bff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "bff_sessions")
class BffSessionEntity {

    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "token_expires_at", nullable = false)
    private long tokenExpiresAt;

    @Column(name = "generation", nullable = false)
    private long generation;

    @Column(name = "refresh_owner")
    private @Nullable UUID refreshOwner;

    @Column(name = "refresh_lease_expires_at")
    private @Nullable Instant refreshLeaseExpiresAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected BffSessionEntity() {}

    BffSessionEntity(UUID sessionId, String payload, long tokenExpiresAt, Instant expiresAt) {
        this.sessionId = sessionId;
        this.payload = payload;
        this.tokenExpiresAt = tokenExpiresAt;
        this.generation = 0;
        this.expiresAt = expiresAt;
    }

    String payload() {
        return this.payload;
    }

    long tokenExpiresAt() {
        return this.tokenExpiresAt;
    }

    long generation() {
        return this.generation;
    }

    @Nullable UUID refreshOwner() {
        return this.refreshOwner;
    }

    @Nullable Instant refreshLeaseExpiresAt() {
        return this.refreshLeaseExpiresAt;
    }

    Instant expiresAt() {
        return this.expiresAt;
    }

    void reset(String payload, long tokenExpiresAt, Instant expiresAt) {
        this.payload = payload;
        this.tokenExpiresAt = tokenExpiresAt;
        this.generation = 0;
        this.refreshOwner = null;
        this.refreshLeaseExpiresAt = null;
        this.expiresAt = expiresAt;
    }

    void claimRefresh(UUID owner, Instant leaseExpiresAt) {
        this.refreshOwner = owner;
        this.refreshLeaseExpiresAt = leaseExpiresAt;
    }

    boolean isRefreshOwnedBy(UUID owner) {
        return owner.equals(this.refreshOwner);
    }

    void completeRefresh(String payload, long tokenExpiresAt) {
        this.payload = payload;
        this.tokenExpiresAt = tokenExpiresAt;
        this.generation++;
        this.refreshOwner = null;
        this.refreshLeaseExpiresAt = null;
    }

    void releaseRefresh() {
        this.refreshOwner = null;
        this.refreshLeaseExpiresAt = null;
    }
}
