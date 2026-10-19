package io.taskmigo.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QuerySchemaView;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

final class FilterByOpenApiCustomizer implements OperationCustomizer {

    private static final String EXTENSION = "x-filter-by";
    private static final Comparator<QueryOperator> OPERATOR_ORDER = Comparator.comparingInt(QueryOperator::ordinal);

    private final List<QuerySchemaView> schemas;

    FilterByOpenApiCustomizer(List<QuerySchemaView> schemas) {
        this.schemas = List.copyOf(Objects.requireNonNull(schemas));
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Optional<Parameter> filterBy = filterByParameter(operation);
        if (filterBy.isEmpty()) {
            return operation;
        }

        QueryOperation queryOperation = handlerMethod.getMethodAnnotation(QueryOperation.class);
        if (queryOperation == null) {
            throw new IllegalStateException("filterBy handler must declare @QueryOperation");
        }

        filterBy.orElseThrow().addExtension(EXTENSION, metadata(this.schema(queryOperation.value())));
        return operation;
    }

    private QuerySchemaView schema(String operation) {
        List<QuerySchemaView> matches = this.schemas
            .stream()
            .filter(candidate -> candidate.operation().equals(operation))
            .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                "Expected exactly one Query Schema for operation " + operation + ", found " + matches.size()
            );
        }
        return matches.getFirst();
    }

    private static Optional<Parameter> filterByParameter(Operation operation) {
        if (operation.getParameters() == null) {
            return Optional.empty();
        }
        return operation
            .getParameters()
            .stream()
            .filter(parameter -> "filterBy".equals(parameter.getName()) && "query".equals(parameter.getIn()))
            .findFirst();
    }

    private static Map<String, Object> metadata(QuerySchemaView schema) {
        List<QueryFieldDescriptor> fields = schema
            .fields()
            .stream()
            .filter(field -> !field.operators().isEmpty())
            .sorted(Comparator.comparing(field -> field.path().text()))
            .toList();

        LinkedHashMap<String, Object> documentedFields = new LinkedHashMap<>();
        for (QueryFieldDescriptor field : fields) {
            documentedFields.put(
                field.path().text(),
                field.operators().stream().sorted(OPERATOR_ORDER).map(Enum::name).toList()
            );
        }
        return documentedFields;
    }
}
