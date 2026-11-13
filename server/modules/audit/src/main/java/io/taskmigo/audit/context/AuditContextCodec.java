package io.taskmigo.audit.context;

import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/// Versioned codec for carrying immutable audit attribution in trusted, durable job metadata.
///
/// Decode only after validating that the job envelope came from a trusted producer:
/// this format is not a signature and does not authorize impersonation.
public final class AuditContextCodec {

    private static final int VERSION = 1;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private AuditContextCodec() {}

    /// Serializes the responsible actor and typed provenance with a schema version.
    public static String encode(AuditContext context) {
        Objects.requireNonNull(context);
        return JSON.writeValueAsString(new Envelope(VERSION, context));
    }

    /// Restores an actor from trusted job metadata and rejects unknown schema versions.
    public static AuditContext decodeTrusted(String encoded) {
        Objects.requireNonNull(encoded);
        Envelope envelope = JSON.readValue(encoded, Envelope.class);
        if (envelope.version() != VERSION) {
            throw new IllegalArgumentException("Unsupported audit context version: " + envelope.version());
        }
        return Objects.requireNonNull(envelope.context());
    }

    /// Explicit versioned transport record used by durable background jobs.
    public record Envelope(int version, AuditContext context) {}
}
