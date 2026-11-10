package io.taskmigo.ticket.persistence;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModel;
import io.taskmigo.authorization.querybinding.jpa.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.jpaquery.JpaQueryExpressionBinder;
import io.taskmigo.jpaquery.JpaQuerySortBinder;
import io.taskmigo.query.ListQuerySpec;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.model.QueryPredicateModel;
import io.taskmigo.ticket.application.TicketQueryRepository;
import io.taskmigo.ticket.application.api.TicketInfo;
import io.taskmigo.ticket.domain.Ticket;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
class JpaTicketQueryRepositoryAdapter implements TicketQueryRepository {

    private final JpaTicketRepository repository;
    private final ListTicketsQuerySchema listQuerySchema;
    private final ReadTicketAuthorizationSchema readAuthorizationSchema;
    private final UpdateTicketAuthorizationSchema updateAuthorizationSchema;
    private final JpaObjectAuthorizationPredicateBinder<TicketInfo, TicketEntity> listAuthorizationBinder;
    private final JpaObjectAuthorizationPredicateBinder<TicketInfo, TicketEntity> readAuthorizationBinder;
    private final JpaObjectAuthorizationPredicateBinder<TicketInfo, TicketEntity> updateAuthorizationBinder;

    JpaTicketQueryRepositoryAdapter(
        JpaTicketRepository repository,
        ListTicketsQuerySchema listQuerySchema,
        ListTicketsAuthorizationSchema listAuthorizationSchema,
        ReadTicketAuthorizationSchema readAuthorizationSchema,
        UpdateTicketAuthorizationSchema updateAuthorizationSchema
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.listQuerySchema = Objects.requireNonNull(listQuerySchema);
        this.readAuthorizationSchema = Objects.requireNonNull(readAuthorizationSchema);
        this.updateAuthorizationSchema = Objects.requireNonNull(updateAuthorizationSchema);
        this.listAuthorizationBinder = new JpaObjectAuthorizationPredicateBinder<>(
            TicketInfo.class,
            Objects.requireNonNull(listAuthorizationSchema)
        );
        this.readAuthorizationBinder = new JpaObjectAuthorizationPredicateBinder<>(
            TicketInfo.class,
            readAuthorizationSchema
        );
        this.updateAuthorizationBinder = new JpaObjectAuthorizationPredicateBinder<>(
            TicketInfo.class,
            updateAuthorizationSchema
        );
    }

    @Override
    public Optional<Ticket> findAuthorized(
        UUID id,
        ObjectAuthorizationPredicate<TicketInfo> authorization,
        QueryFieldContext context
    ) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(authorization);
        Objects.requireNonNull(context);
        if (authorization instanceof ObjectAuthorizationPredicateModel model) {
            Specification<TicketEntity> target = (root, query, builder) ->
                builder.equal(root.get(TicketEntity_.id), id);
            Specification<TicketEntity> authorizationSpec = this.singleAuthorization(model, authorization, context);
            return this.repository.findOne(target.and(authorizationSpec)).map(TicketEntity::toDomain);
        }
        if (authorization.isAlwaysFalse()) {
            return Optional.empty();
        }
        if (authorization.isAlwaysTrue()) {
            return this.repository.findById(id).map(TicketEntity::toDomain);
        }
        throw new IllegalArgumentException("Unsupported Ticket Object Authorization Predicate implementation");
    }

    @Override
    public OffsetPage<Ticket> list(
        int page,
        int perPage,
        @Nullable UUID ticketTypeId,
        ListQuerySpec<TicketInfo> query,
        ObjectAuthorizationPredicate<TicketInfo> authorization
    ) {
        Objects.requireNonNull(query);
        Objects.requireNonNull(authorization);
        Specification<TicketEntity> authorizationSpec = this.listAuthorization(authorization, query.context());
        Specification<TicketEntity> scope = typeScope(ticketTypeId);
        Specification<TicketEntity> clientFilter = this.bindFilter(query);
        Specification<TicketEntity> ordering = this.sorting(query);
        Sort pageableSort = query.sorting().isEmpty() ? Sort.by(Sort.Direction.ASC, "id") : Sort.unsorted();
        var pageable = PageRequest.of(page - 1, perPage, pageableSort);
        var result = this.repository.findAll(scope.and(authorizationSpec).and(clientFilter).and(ordering), pageable);
        return new OffsetPage<>(
            result.getContent().stream().map(TicketEntity::toDomain).toList(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    private Specification<TicketEntity> listAuthorization(
        ObjectAuthorizationPredicate<TicketInfo> authorization,
        QueryFieldContext context
    ) {
        if (authorization instanceof ObjectAuthorizationPredicateModel) {
            return this.listAuthorizationBinder.bind(authorization, context);
        }
        if (authorization.isAlwaysTrue()) {
            return (root, query, builder) -> builder.conjunction();
        }
        if (authorization.isAlwaysFalse()) {
            return (root, query, builder) -> builder.disjunction();
        }
        throw new IllegalArgumentException("Unsupported Ticket Object Authorization Predicate implementation");
    }

    private Specification<TicketEntity> singleAuthorization(
        ObjectAuthorizationPredicateModel model,
        ObjectAuthorizationPredicate<TicketInfo> authorization,
        QueryFieldContext context
    ) {
        if (model.schemaIdentity().equals(this.readAuthorizationSchema.authorizationIdentity(context))) {
            return this.readAuthorizationBinder.bind(authorization, context);
        }
        if (model.schemaIdentity().equals(this.updateAuthorizationSchema.authorizationIdentity(context))) {
            return this.updateAuthorizationBinder.bind(authorization, context);
        }
        throw new IllegalArgumentException(
            "Ticket Object Authorization Predicate schema identity does not match read/update"
        );
    }

    private Specification<TicketEntity> bindFilter(ListQuerySpec<TicketInfo> query) {
        if (!(query.predicate() instanceof QueryPredicateModel model)) {
            throw new IllegalArgumentException("Unsupported Ticket Query Predicate implementation");
        }
        String expectedIdentity = this.listQuerySchema.identity(query.context());
        if (!model.schemaIdentity().equals(expectedIdentity)) {
            throw new IllegalArgumentException("Ticket Query Predicate schema identity does not match the list schema");
        }
        return JpaQueryExpressionBinder.bind(model.expression(), this.listQuerySchema, query.context());
    }

    private Specification<TicketEntity> sorting(ListQuerySpec<TicketInfo> query) {
        return (root, criteria, builder) -> {
            JpaQuerySortBinder.apply(
                Objects.requireNonNull(criteria),
                root,
                builder,
                this.listQuerySchema,
                query.context(),
                query.sorting()
            );
            return builder.conjunction();
        };
    }

    private static Specification<TicketEntity> typeScope(@Nullable UUID ticketTypeId) {
        return (root, query, builder) ->
            ticketTypeId == null
                ? builder.conjunction()
                : builder.equal(root.get(TicketEntity_.ticketTypeId), ticketTypeId);
    }
}
