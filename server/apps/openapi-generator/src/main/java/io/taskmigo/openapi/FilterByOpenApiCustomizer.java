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

    private static final String FILTER_EXTENSION = "x-filter-by";
    private static final String SORT_EXTENSION = "x-sort-by";
    private static final Comparator<QueryOperator> OPERATOR_ORDER = Comparator.comparingInt(QueryOperator::ordinal);

    private final List<QuerySchemaView> schemas;

    FilterByOpenApiCustomizer(List<QuerySchemaView> schemas) {
        this.schemas = List.copyOf(Objects.requireNonNull(schemas));
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Optional<Parameter> filterBy = parameter(operation, "filterBy");
        Optional<Parameter> sortBy = parameter(operation, "sortBy");
        if (filterBy.isEmpty() && sortBy.isEmpty()) {
            return operation;
        }

        QueryOperation queryOperation = handlerMethod.getMethodAnnotation(QueryOperation.class);
        if (queryOperation == null) {
            throw new IllegalStateException("query handler must declare @QueryOperation");
        }

        QuerySchemaView schema = this.schema(queryOperation.value());
        filterBy.ifPresent(parameter -> parameter.addExtension(FILTER_EXTENSION, filterMetadata(schema)));
        List<String> sortableFields = sortMetadata(schema);
        if (!sortableFields.isEmpty()) {
            sortBy.ifPresent(parameter -> parameter.addExtension(SORT_EXTENSION, sortableFields));
        }
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

    private static Optional<Parameter> parameter(Operation operation, String name) {
        if (operation.getParameters() == null) {
            return Optional.empty();
        }
        return operation
            .getParameters()
            .stream()
            .filter(parameter -> name.equals(parameter.getName()) && "query".equals(parameter.getIn()))
            .findFirst();
    }

    private static Map<String, Object> filterMetadata(QuerySchemaView schema) {
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

    private static List<String> sortMetadata(QuerySchemaView schema) {
        return schema
            .fields()
            .stream()
            .filter(QueryFieldDescriptor::sortable)
            .map(field -> field.path().text())
            .sorted()
            .toList();
    }
}
