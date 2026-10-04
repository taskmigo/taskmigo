package io.taskmigo.query;

import io.taskmigo.language.CompilationFeature;
import io.taskmigo.language.CompilationMode;
import io.taskmigo.language.CompilationProfile;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.CompilerEnvironment;
import io.taskmigo.language.EmbeddedLanguageException;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceSchemaResolver;
import io.taskmigo.language.SchemaContext;
import io.taskmigo.query.model.QueryExpression;
import java.util.Map;
import java.util.Set;
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
    public <Q> QueryPredicate<Q> compile(ResourceSchema schema, QueryBinding<Q> binding, @Nullable String source) {
        requireCompatible(schema, binding);
        try {
            if (source == null || source.isBlank()) {
                return QueryPredicateFactory.alwaysTrue(binding);
            }
            ResourceSchema effectiveSchema = ResourceSchemaResolver.fixed(Map.of(schema.type(), schema)).resolve(
                schema.type(),
                SchemaContext.EMPTY
            );
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

    /// Compiles bindings selected through Spring's generic type resolution.
    public QueryPredicate<?> compileUntyped(ResourceSchema schema, QueryBinding<?> binding, @Nullable String source) {
        return this.compile(schema, binding, source);
    }

    private static void requireCompatible(ResourceSchema schema, QueryBinding<?> binding) {
        if (
            !schema.type().equals(binding.resourceType()) || !schema.fingerprint().equals(binding.schemaFingerprint())
        ) {
            throw new IllegalArgumentException("query binding is incompatible with resource schema");
        }
    }
}
