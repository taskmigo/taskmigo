package io.taskmigo.history;

import io.taskmigo.foundation.CursorPage;
import io.taskmigo.foundation.PageLimit;
import io.taskmigo.project.ProjectChanged;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/// Persists and reads immutable project audit entries.
@Service
public class ProjectHistory {

    private static final PageLimit PAGE_LIMIT = new PageLimit(20, 100);
    private static final Sort HISTORY_SORT = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
    private static final TypeReference<List<ProjectChanged.Change>> CHANGES_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> DATA_TYPE = new TypeReference<>() {};

    private final ProjectHistoryRepository entries;
    private final JsonMapper json;

    ProjectHistory(ProjectHistoryRepository entries, JsonMapper json) {
        this.entries = entries;
        this.json = json;
    }

    @EventListener
    void on(ProjectChanged event) {
        this.entries.saveAndFlush(
            new ProjectHistoryEntity(event, this.write(event.changes()), this.write(event.data()))
        );
    }

    @Transactional(readOnly = true)
    public CursorPage<Entry> list(UUID projectId, @Nullable String cursor, int limit) {
        int pageSize = PAGE_LIMIT.require(limit);
        Pageable pageable = PageRequest.of(0, pageSize + 1, HISTORY_SORT);
        Specification<ProjectHistoryEntity> specification = projectId(projectId);
        if (cursor != null && !cursor.isBlank()) specification = specification.and(before(decode(cursor)));
        List<ProjectHistoryEntity> rows = this.entries.findAll(specification, pageable).getContent();
        boolean hasMore = rows.size() > pageSize;
        List<ProjectHistoryEntity> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<Entry> items = pageRows.stream().map(this::toEntry).toList();
        return new CursorPage<>(items, hasMore ? encode(pageRows.getLast()) : null);
    }

    private static Specification<ProjectHistoryEntity> projectId(UUID projectId) {
        return (root, query, builder) -> builder.equal(root.get("projectId"), projectId);
    }

    private static Specification<ProjectHistoryEntity> before(Cursor cursor) {
        return (root, query, builder) ->
            builder.or(
                builder.lessThan(root.get("occurredAt"), cursor.occurredAt()),
                builder.and(
                    builder.equal(root.get("occurredAt"), cursor.occurredAt()),
                    builder.lessThan(root.get("id"), cursor.id())
                )
            );
    }

    private Entry toEntry(ProjectHistoryEntity entity) {
        ProjectChanged.@Nullable Target target =
            entity.targetType == null
                ? null
                : new ProjectChanged.Target(
                      entity.targetType,
                      Objects.requireNonNull(entity.targetId),
                      Objects.requireNonNull(entity.targetDisplayName)
                  );
        return new Entry(
            entity.id,
            entity.projectId,
            entity.action,
            new ProjectChanged.Actor(entity.actorType, entity.actorId, entity.actorDisplayName),
            target,
            this.read(entity.changesJson, CHANGES_TYPE),
            this.read(entity.dataJson, DATA_TYPE),
            entity.occurredAt
        );
    }

    private String write(Object value) {
        try {
            return this.json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize project history", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return this.json.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not deserialize project history", exception);
        }
    }

    private static String encode(ProjectHistoryEntity entity) {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString((entity.occurredAt + "|" + entity.id).getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            if (separator < 1) throw new IllegalArgumentException();
            return new Cursor(
                Instant.parse(raw.substring(0, separator)),
                UUID.fromString(raw.substring(separator + 1))
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid history cursor", exception);
        }
    }

    public record Entry(
        UUID id,
        UUID projectId,
        ProjectChanged.Action action,
        ProjectChanged.Actor actor,
        ProjectChanged.@Nullable Target target,
        List<ProjectChanged.Change> changes,
        Map<String, Object> data,
        Instant occurredAt
    ) {}

    private record Cursor(Instant occurredAt, UUID id) {}
}
