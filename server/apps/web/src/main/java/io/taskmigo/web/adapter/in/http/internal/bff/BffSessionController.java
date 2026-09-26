package io.taskmigo.web.adapter.in.http.internal.bff;

import io.taskmigo.web.composition.bff.BffSessionCoordinator;
import io.taskmigo.web.composition.bff.BffSessionCoordinator.Leader;
import io.taskmigo.web.composition.bff.BffSessionCoordinator.Missing;
import io.taskmigo.web.composition.bff.BffSessionCoordinator.Ready;
import io.taskmigo.web.composition.bff.BffSessionCoordinator.RefreshClaim;
import io.taskmigo.web.composition.bff.BffSessionCoordinator.Wait;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/_internal/bff/sessions")
class BffSessionController {

    private final BffSessionCoordinator coordinator;

    BffSessionController(BffSessionCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @PutMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void ensure(@PathVariable UUID sessionId, @Valid @RequestBody SessionRequest request) {
        this.coordinator.ensure(
                sessionId,
                request.payload(),
                request.tokenExpiresAt(),
                Instant.ofEpochMilli(request.expiresAt())
            );
    }

    @PostMapping("/{sessionId}/refresh-claims")
    ResponseEntity<ClaimResponse> claim(
        @PathVariable UUID sessionId,
        @Valid @RequestBody ClaimRequest request
    ) {
        RefreshClaim claim = this.coordinator.claimRefresh(
            sessionId,
            request.owner(),
            request.refreshSkewMilliseconds()
        );
        if (claim instanceof Missing) {
            return ResponseEntity.notFound().build();
        }
        if (claim instanceof Ready ready) {
            return ResponseEntity.ok(ClaimResponse.ready(ready.snapshot().payload(), ready.snapshot().generation()));
        }
        if (claim instanceof Leader leader) {
            return ResponseEntity.ok(ClaimResponse.leader(leader.snapshot().payload(), leader.snapshot().generation()));
        }
        Wait wait = (Wait) claim;
        return ResponseEntity.ok(ClaimResponse.waiting(wait.retryAfterMilliseconds()));
    }

    @PutMapping("/{sessionId}/refresh-claims/{owner}")
    ResponseEntity<Void> complete(
        @PathVariable UUID sessionId,
        @PathVariable UUID owner,
        @Valid @RequestBody CompleteRequest request
    ) {
        if (!this.coordinator.completeRefresh(sessionId, owner, request.payload(), request.tokenExpiresAt())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{sessionId}/refresh-claims/{owner}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable UUID sessionId, @PathVariable UUID owner) {
        this.coordinator.releaseRefresh(sessionId, owner);
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID sessionId) {
        this.coordinator.delete(sessionId);
    }

    record SessionRequest(
        @NotBlank String payload,
        @PositiveOrZero long tokenExpiresAt,
        @PositiveOrZero long expiresAt
    ) {}

    record ClaimRequest(UUID owner, @PositiveOrZero long refreshSkewMilliseconds) {}

    record CompleteRequest(@NotBlank String payload, @PositiveOrZero long tokenExpiresAt) {}

    record ClaimResponse(
        String state,
        @Nullable String payload,
        @Nullable Long generation,
        @Nullable Long retryAfterMilliseconds
    ) {
        static ClaimResponse ready(String payload, long generation) {
            return new ClaimResponse("ready", payload, generation, null);
        }

        static ClaimResponse leader(String payload, long generation) {
            return new ClaimResponse("leader", payload, generation, null);
        }

        static ClaimResponse waiting(long retryAfterMilliseconds) {
            return new ClaimResponse("wait", null, null, retryAfterMilliseconds);
        }
    }
}
