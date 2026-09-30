package io.taskmigo.audit.adapter.out.jobrunr;

import org.jobrunr.utils.mapper.JsonMapper;
import org.jobrunr.utils.mapper.jackson3.Jackson3JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/// Creates the JobRunr JSON mapper used for durable audit-event transport.
///
/// JobRunr 8.6.1 does not yet allow the JDK immutable list implementations produced by
/// `List.of`, `List.copyOf`, and `Stream.toList`. Audit events intentionally use those
/// immutable collections, so the durable transport must explicitly allow only those two
/// concrete list implementations during polymorphic deserialization.
public final class AuditJobRunrJsonMapperFactory {

    private static final String IMMUTABLE_LIST_SMALL = "java.util.ImmutableCollections$List12";
    private static final String IMMUTABLE_LIST_GENERAL = "java.util.ImmutableCollections$ListN";

    private AuditJobRunrJsonMapperFactory() {}

    /// Creates a JobRunr mapper that can round-trip immutable audit payload lists.
    public static JsonMapper create() {
        var validator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType(IMMUTABLE_LIST_SMALL)
            .allowIfSubType(IMMUTABLE_LIST_GENERAL);
        return new Jackson3JsonMapper(validator);
    }
}
