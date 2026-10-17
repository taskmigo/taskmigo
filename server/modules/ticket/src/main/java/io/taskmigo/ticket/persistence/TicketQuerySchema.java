package io.taskmigo.ticket.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.GT;
import static io.taskmigo.query.QueryOperator.GTE;
import static io.taskmigo.query.QueryOperator.LT;
import static io.taskmigo.query.QueryOperator.LTE;
import static io.taskmigo.query.QueryOperator.NE;

import io.taskmigo.eav.query.jpa.EavJpaQueryFieldProvider;
import io.taskmigo.jpaquery.JpaPath;
import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

abstract class TicketQuerySchema extends QuerySchema<TicketEntity> {

    private final TicketQueryFieldCatalog runtimeFields;
    private final EavJpaQueryFieldProvider eavQueryFields;
    private final boolean listOperation;

    TicketQuerySchema(
        TicketQueryFieldCatalog runtimeFields,
        EavJpaQueryFieldProvider eavQueryFields,
        boolean listOperation
    ) {
        super(TicketEntity.class);
        this.runtimeFields = Objects.requireNonNull(runtimeFields);
        this.eavQueryFields = Objects.requireNonNull(eavQueryFields);
        this.listOperation = listOperation;
    }

    @Override
    protected final Collection<QueryField<TicketEntity, ?>> staticFields() {
        QueryField<TicketEntity, String> title = field(TicketEntity_.title).operators(EQ, NE, CONTAINS);
        QueryField<TicketEntity, String> status = field(TicketEntity_.status).operators(EQ, NE, CONTAINS);
        QueryField<TicketEntity, Instant> createdAt = field(TicketEntity_.createdAt).operators(
            EQ,
            NE,
            GT,
            GTE,
            LT,
            LTE
        );
        QueryField<TicketEntity, Instant> updatedAt = field(TicketEntity_.updatedAt).operators(
            EQ,
            NE,
            GT,
            GTE,
            LT,
            LTE
        );
        if (this.listOperation) {
            title = title.sortable();
            status = status.sortable();
            createdAt = createdAt.sortable();
            updatedAt = updatedAt.sortable();
        }
        return List.of(
            field(TicketEntity_.id).operators(EQ, NE),
            field(TicketEntity_.ticketTypeId).operators(EQ, NE),
            title,
            status,
            field(TicketEntity_.createdBy).operators(EQ, NE),
            createdAt,
            updatedAt
        );
    }

    @Override
    protected final Collection<QueryField<TicketEntity, ?>> runtimeFields(QueryFieldContext context) {
        JpaPath<TicketEntity, UUID> schemaInstancePath = JpaPath.of(TicketEntity_.schemaInstanceId);
        List<QueryField<TicketEntity, ?>> fields = new ArrayList<>();
        this.runtimeFields
            .fields(context)
            .values()
            .forEach(field -> fields.add(this.eavQueryFields.create(field, schemaInstancePath)));
        return List.copyOf(fields);
    }
}
