package io.taskmigo.authorization.request.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// Retains only the newest observed derivative for each Statement while reclaiming derivatives that become idle.
final class StatementArtifactCache<T> {

    private final Cache<UUID, T> entries;

    private StatementArtifactCache(Duration idleTtl, Ticker ticker) {
        this.entries = Caffeine.newBuilder().expireAfterAccess(idleTtl).ticker(ticker).build();
    }

    static <T> StatementArtifactCache<T> expireAfterAccess(Duration idleTtl) {
        return new StatementArtifactCache<>(idleTtl, Ticker.systemTicker());
    }

    static <T> StatementArtifactCache<T> expireAfterAccess(Duration idleTtl, Ticker ticker) {
        return new StatementArtifactCache<>(idleTtl, ticker);
    }

    T getOrCreate(
        UUID statementId,
        Instant revision,
        Function<T, Instant> revisionOf,
        Predicate<T> matches,
        Supplier<T> create
    ) {
        T latest = this.entries.getIfPresent(statementId);
        if (latest != null && matches.test(latest)) {
            return latest;
        }

        T created = create.get();
        this.entries.asMap().compute(statementId, (ignored, retained) -> {
            if (retained != null && (matches.test(retained) || revisionOf.apply(retained).isAfter(revision))) {
                return retained;
            }
            return created;
        });
        return created;
    }
}
