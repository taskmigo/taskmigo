package io.taskmigo.query;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.language.CompilationFeature;
import io.taskmigo.language.CompilationMode;
import io.taskmigo.language.CompilationProfile;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.EmbeddedLanguageException;
import io.taskmigo.language.EnvironmentSchema;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageType;
import io.taskmigo.query.model.QueryExpression;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/// Compiles the optional HTTP filterBy expression against an explicit operation-scoped Query Schema.
@Service
public class FilterByCompiler {

    private static final CompilationProfile PROFILE = new CompilationProfile(
        CompilationMode.EXPRESSION,
        Set.of(
            CompilationFeature.LOGICAL_OPERATORS,
            CompilationFeature.EQUALITY_OPERATORS,
            CompilationFeature.ORDERING_OPERATORS,
            CompilationFeature.CONTAINS_INTRINSIC
        )
    );

    private final LanguageCompiler compiler;

    /// Creates a filter compiler using the default Language limits.
    public FilterByCompiler() {
        this(new LanguageCompiler());
    }

    /// Creates a filter compiler with an application-configured Language compiler.
    public FilterByCompiler(LanguageCompiler compiler) {
        this.compiler = compiler;
    }

    /// Compiles against an operation-scoped schema and its runtime field context.
    public QueryPredicate<?> compile(QuerySchemaView schema, QueryFieldContext context, @Nullable String source) {
        String identity = schema.identity(context);
        if (source == null || source.isBlank()) {
            return QueryPredicateFactory.wrap(identity, new QueryExpression.Literal(true));
        }
        try {
            CompiledSource compiled = this.compiler.compile(source, environment(schema, context), PROFILE);
            if (compiled.resultType() != LanguageType.Scalar.BOOL) {
                throw new FilterByException("filterBy expression must return Bool");
            }
            QueryExpression expression = compiled.map(LanguageQueryExpressionVisitor.INSTANCE);
            QuerySchemaValidator.validate(expression, schema, context);
            return QueryPredicateFactory.wrap(identity, expression);
        } catch (EmbeddedLanguageException | IllegalArgumentException exception) {
            throw new FilterByException("Invalid filterBy expression", exception);
        }
    }

    /// Compiles against an operation-scoped schema with an empty runtime context.
    public QueryPredicate<?> compile(QuerySchemaView schema, @Nullable String source) {
        return this.compile(schema, QueryFieldContext.empty(), source);
    }

    private static EnvironmentSchema environment(QuerySchemaView schema, QueryFieldContext context) {
        return environment("query-filter:" + schema.identity(context), schema.operation(), schema.fields(context));
    }

    private static EnvironmentSchema environment(
        String identity,
        String typeName,
        Collection<QueryFieldDescriptor> fields
    ) {
        Map<String, EnvironmentSchema.Field> rootFields = new HashMap<>();
        for (QueryFieldDescriptor field : fields) {
            List<String> segments = field.path().segments();
            String first = segments.getFirst();
            rootFields.put(
                first,
                segments.size() == 1 && fields.stream().noneMatch(candidate -> isNestedUnder(candidate, List.of(first)))
                    ? toField(field)
                    : nestedField(fields, List.of(first))
            );
        }
        return new EnvironmentSchema(
            identity,
            Map.of(
                "object",
                new EnvironmentSchema.Root(
                    new EnvironmentSchema.Field(new LanguageType.StructuredType(typeName, rootFields), false, true),
                    rootFields
                )
            )
        );
    }

    private static EnvironmentSchema.Field nestedField(Collection<QueryFieldDescriptor> fields, List<String> prefix) {
        Map<String, EnvironmentSchema.Field> children = fields
            .stream()
            .filter(field -> isNestedUnder(field, prefix))
            .collect(
                Collectors.toMap(
                    field -> field.path().segments().get(prefix.size()),
                    field -> {
                        List<String> childPrefix = append(prefix, field.path().segments().get(prefix.size()));
                        return field.path().segments().size() == childPrefix.size() &&
                            fields.stream().noneMatch(candidate -> isNestedUnder(candidate, childPrefix))
                            ? toField(field)
                            : nestedField(fields, childPrefix);
                    },
                    (left, right) -> left
                )
            );
        return new EnvironmentSchema.Field(
            new LanguageType.StructuredType(String.join(".", prefix), children),
            false,
            true
        );
    }

    private static boolean isNestedUnder(QueryFieldDescriptor field, List<String> prefix) {
        List<String> segments = field.path().segments();
        return segments.size() > prefix.size() && segments.subList(0, prefix.size()).equals(prefix);
    }

    private static List<String> append(List<String> prefix, String segment) {
        ArrayList<String> result = new ArrayList<>(prefix.size() + 1);
        result.addAll(prefix);
        result.add(segment);
        return List.copyOf(result);
    }

    private static EnvironmentSchema.Field toField(QueryFieldDescriptor field) {
        return new EnvironmentSchema.Field(toLanguageType(field.type()), field.nullable(), true);
    }

    private static LanguageType toLanguageType(TypeDescriptor type) {
        Class<?> raw = type.rawType();
        if (
            raw == String.class ||
            raw == Character.class ||
            raw == char.class ||
            raw == UUID.class ||
            raw.isEnum() ||
            TemporalAccessor.class.isAssignableFrom(raw)
        ) {
            return LanguageType.Scalar.STRING;
        }
        if (raw == Boolean.class || raw == boolean.class) {
            return LanguageType.Scalar.BOOL;
        }
        if (Number.class.isAssignableFrom(raw) || raw.isPrimitive()) {
            return LanguageType.Scalar.NUMBER;
        }
        if (Collection.class.isAssignableFrom(raw)) {
            TypeDescriptor elementType = type.typeArguments().isEmpty()
                ? TypeDescriptor.of(Object.class)
                : type.typeArguments().getFirst();
            return new LanguageType.ListType(toLanguageType(elementType));
        }
        return new LanguageType.StructuredType(raw.getName(), Map.of());
    }
}
