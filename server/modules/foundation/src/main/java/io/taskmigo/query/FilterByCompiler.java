package io.taskmigo.query;

import io.taskmigo.embeddedlanguage.CompilationFeature;
import io.taskmigo.embeddedlanguage.CompilationMode;
import io.taskmigo.embeddedlanguage.CompilationProfile;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageCompiler;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageException;
import io.taskmigo.embeddedlanguage.EnvironmentSchema;
import io.taskmigo.embeddedlanguage.LanguageType;
import io.taskmigo.embeddedlanguage.SemanticAst;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Service;

/// Compiles the optional HTTP filterBy expression against an explicit Query Schema.
@Service
@SuppressWarnings({ "checkstyle:NeedBraces", "checkstyle:OneStatementPerLine", "checkstyle:UnusedLocalVariable" })
public class FilterByCompiler {
    private final EmbeddedLanguageCompiler compiler;

    /// Creates a filter compiler using the default Embedded Language limits.
    public FilterByCompiler() {
        this(new EmbeddedLanguageCompiler());
    }

    /// Creates a filter compiler with an application-configured language compiler.
    public FilterByCompiler(EmbeddedLanguageCompiler compiler) {
        this.compiler = compiler;
    }

    /// Compiles blank input as an always-true predicate and rejects every non-Boolean source.
    public <Q> QueryPredicate<Q> compile(QuerySchema<Q> schema, @Nullable String source) {
        if (source == null || source.isBlank()) return QueryPredicateFactory.alwaysTrue(schema);
        try {
            EnvironmentSchema environment = environment(schema);
            CompilationProfile profile = new CompilationProfile(
                CompilationMode.EXPRESSION,
                Set.of(
                    CompilationFeature.LOGICAL_OPERATORS,
                    CompilationFeature.EQUALITY_OPERATORS,
                    CompilationFeature.ORDERING_OPERATORS,
                    CompilationFeature.ARITHMETIC_OPERATORS,
                    CompilationFeature.LIST_LITERALS,
                    CompilationFeature.MEMBERSHIP,
                    CompilationFeature.COLLECTION_QUANTIFIERS,
                    CompilationFeature.LENGTH_INTRINSIC
                )
            );
            SemanticAst compiled = this.compiler.compile(source, environment, profile);
            if (compiled.resultType() != LanguageType.Scalar.BOOL) throw new FilterByException("filterBy expression must return Bool");
            QuerySchemaValidator.validate(compiled.expression(), schema);
            return QueryPredicateFactory.from(schema, compiled.expression());
        } catch (FilterByException exception) {
            throw exception;
        } catch (EmbeddedLanguageException | IllegalArgumentException exception) {
            throw new FilterByException("Invalid filterBy expression", exception);
        }
    }

    /// Compiles a schema selected through Spring's generic type resolution.
    public QueryPredicate<?> compileUntyped(QuerySchema<?> schema, @Nullable String source) {
        return this.compileUntypedInternal(schema, source);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private QueryPredicate<?> compileUntypedInternal(QuerySchema<?> schema, @Nullable String source) {
        return compile((QuerySchema) schema, source);
    }

    private static <Q> EnvironmentSchema environment(QuerySchema<Q> schema) {
        Map<String, EnvironmentSchema.Field> fields = new HashMap<>();
        for (QueryField field : schema.fields()) {
            List<String> segments = field.path().segments();
            if (segments.size() == 1) fields.put(segments.getFirst(), toField(field));
            else fields.putIfAbsent(segments.getFirst(), nestedField(schema, segments.getFirst()));
        }
        return new EnvironmentSchema(
            "query-filter:" + schema.identity(),
            Map.of("object", new EnvironmentSchema.Root(
                new EnvironmentSchema.Field(structured(schema), false, true), fields
            ))
        );
    }

    private static <Q> EnvironmentSchema.Field nestedField(QuerySchema<Q> schema, String prefix) {
        List<String> prefixSegments = List.of(prefix.split("\\."));
        Map<String, EnvironmentSchema.Field> children = schema.fields().stream()
            .filter(field -> field.path().segments().size() > prefixSegments.size()
                && field.path().segments().subList(0, prefixSegments.size()).equals(prefixSegments))
            .collect(Collectors.toMap(
                field -> field.path().segments().get(prefixSegments.size()),
                field -> field.path().segments().size() == prefixSegments.size() + 1
                    ? toField(field)
                    : nestedField(schema, prefix + "." + field.path().segments().get(prefixSegments.size())),
                (left, right) -> left
            ));
        return new EnvironmentSchema.Field(new LanguageType.StructuredType(prefix, children), false, true);
    }

    private static <Q> LanguageType.StructuredType structured(QuerySchema<Q> schema) {
        Map<String, EnvironmentSchema.Field> fields = new HashMap<>();
        for (QueryField field : schema.fields()) {
            String first = field.path().segments().getFirst();
            fields.put(first, field.path().segments().size() == 1 ? toField(field) : nestedField(schema, first));
        }
        return new LanguageType.StructuredType(schema.queryType().getName(), fields);
    }

    private static EnvironmentSchema.Field toField(QueryField field) {
        return new EnvironmentSchema.Field(toLanguageType(field.type()), field.nullable(), true);
    }

    private static LanguageType toLanguageType(ResolvableType type) {
        Class<?> raw = type.resolve(Object.class);
        if (raw == String.class || raw == Character.class || raw == char.class || raw == UUID.class) return LanguageType.Scalar.STRING;
        if (raw == Boolean.class || raw == boolean.class) return LanguageType.Scalar.BOOL;
        if (Number.class.isAssignableFrom(raw) || raw.isPrimitive()) return LanguageType.Scalar.NUMBER;
        if (Collection.class.isAssignableFrom(raw)) return new LanguageType.ListType(toLanguageType(type.getGeneric(0)));
        return new LanguageType.StructuredType(raw.getName(), Map.of());
    }

}
