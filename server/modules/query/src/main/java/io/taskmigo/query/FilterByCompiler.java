package io.taskmigo.query;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.language.CompilationFeature;
import io.taskmigo.language.CompilationMode;
import io.taskmigo.language.CompilationProfile;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.CompilerEnvironment;
import io.taskmigo.language.EmbeddedLanguageException;
import io.taskmigo.language.Field;
import io.taskmigo.language.FieldId;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceSchemaResolver;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaContext;
import io.taskmigo.language.SchemaFingerprint;
import io.taskmigo.query.model.QueryExpression;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/// Compiles optional HTTP filters against semantic resource schemas and separate execution bindings.
@Service
public class FilterByCompiler {

    private static final CompilationProfile PROFILE = new CompilationProfile(
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

    private final LanguageCompiler compiler;

    /// Creates a filter compiler using the default Language limits.
    public FilterByCompiler() {
        this(new LanguageCompiler());
    }

    /// Creates a filter compiler with an application-configured Language compiler.
    public FilterByCompiler(LanguageCompiler compiler) {
        this.compiler = compiler;
    }

    /// Compiles blank input as an always-true predicate and rejects incompatible schemas or execution bindings.
    public <Q> QueryPredicate<Q> compile(
        ResourceSchema schema,
        QueryBinding<Q> binding,
        @Nullable String source
    ) {
        try {
            requireCompatible(schema, binding);
            if (source == null || source.isBlank()) {
                return QueryPredicateFactory.alwaysTrue(binding);
            }
            ResourceSchema effectiveSchema = ResourceSchemaResolver.fixed(Map.of(schema.type(), schema))
                .resolve(schema.type(), SchemaContext.EMPTY);
            CompilerEnvironment environment = CompilerEnvironment.of(
                Map.of("object", new CompilerEnvironment.Root(effectiveSchema, true))
            );
            CompiledSource compiled = this.compiler.compile(source, environment, PROFILE);
            if (compiled.resultType() != LanguageType.Scalar.BOOL) {
                throw new FilterByException("filterBy expression must return Bool");
            }
            QueryExpression expression = compiled.map(LanguageQueryExpressionVisitor.INSTANCE);
            QueryBindingValidator.validate(expression, binding);
            return QueryPredicateFactory.from(binding, expression);
        } catch (EmbeddedLanguageException | IllegalArgumentException exception) {
            throw new FilterByException("Invalid filterBy expression", exception);
        }
    }

    /// Temporary source-compatibility overload for application declarations being migrated to bindings.
    public <Q> QueryPredicate<Q> compile(QuerySchema<Q> schema, @Nullable String source) {
        return this.compile(legacySchema(schema), legacyBinding(schema), source);
    }

    /// Compiles bindings selected through Spring's generic type resolution.
    public QueryPredicate<?> compileUntyped(
        ResourceSchema schema,
        QueryBinding<?> binding,
        @Nullable String source
    ) {
        return this.compile(schema, binding, source);
    }

    /// Temporary source-compatibility overload for application declarations being migrated to bindings.
    public QueryPredicate<?> compileUntyped(QuerySchema<?> schema, @Nullable String source) {
        return this.compile(schema, source);
    }

    private static <Q> ResourceSchema legacySchema(QuerySchema<Q> schema) {
        ResourceType type = ResourceType.of("legacy:query:" + schema.queryType().getName());
        List<Field> fields = schema.fields()
            .stream()
            .map(field -> new Field(
                FieldId.of(type.value() + ':' + field.path().text()),
                FieldPath.parse(field.path().text()),
                toLanguageType(field.type()),
                field.nullable()
            ))
            .toList();
        return ResourceSchema.of(type, fields);
    }

    private static <Q> QueryBinding<Q> legacyBinding(QuerySchema<Q> schema) {
        ResourceSchema semantic = legacySchema(schema);
        ResourceType type = semantic.type();
        List<QueryFieldBinding> fields = schema.fields()
            .stream()
            .map(field -> new QueryFieldBinding(
                FieldId.of(type.value() + ':' + field.path().text()),
                field.path(),
                field.operators()
            ))
            .toList();
        return new QueryBinding<>() {
            @Override
            public Class<Q> queryType() {
                return schema.queryType();
            }

            @Override
            public ResourceType resourceType() {
                return type;
            }

            @Override
            public SchemaFingerprint schemaFingerprint() {
                return semantic.fingerprint();
            }

            @Override
            public Optional<QueryFieldBinding> field(FieldId id) {
                return fields.stream().filter(field -> field.id().equals(id)).findFirst();
            }

            @Override
            public Collection<QueryFieldBinding> fields() {
                return fields;
            }
        };
    }

    private static LanguageType toLanguageType(TypeDescriptor type) {
        Class<?> raw = type.rawType();
        if (raw == String.class || raw == Character.class || raw == char.class || raw == UUID.class) {
            return LanguageType.Scalar.STRING;
        }
        if (raw == Boolean.class || raw == boolean.class) {
            return LanguageType.Scalar.BOOL;
        }
        if (Number.class.isAssignableFrom(raw) || raw.isPrimitive()) {
            return LanguageType.Scalar.NUMBER;
        }
        if (Collection.class.isAssignableFrom(raw)) {
            TypeDescriptor element = type.typeArguments().isEmpty()
                ? TypeDescriptor.of(Object.class)
                : type.typeArguments().getFirst();
            return new LanguageType.ListType(toLanguageType(element));
        }
        return new LanguageType.StructuredType(raw.getName(), Map.of());
    }

    private static void requireCompatible(ResourceSchema schema, QueryBinding<?> binding) {
        if (!schema.type().equals(binding.resourceType()) || !schema.fingerprint().equals(binding.schemaFingerprint())) {
            throw new IllegalArgumentException("query binding is incompatible with resource schema");
        }
    }
}
