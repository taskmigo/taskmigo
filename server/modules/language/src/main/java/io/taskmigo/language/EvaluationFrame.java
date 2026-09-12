package io.taskmigo.language;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/// Holds one allocation-light evaluation scope for roots and restricted-lambda bindings.
@NullUnmarked
final class EvaluationFrame {

    private static final String LAMBDA_ROOT = "__lambda__";
    private static final int INITIAL_BINDING_CAPACITY = 4;
    private static final String[] EMPTY_BINDING_NAMES = new String[0];
    private static final Object[] EMPTY_BINDING_VALUES = new Object[0];

    private final Map<String, ?> roots;
    private String[] bindingNames = EMPTY_BINDING_NAMES;
    private Object[] bindingValues = EMPTY_BINDING_VALUES;
    private int bindingCount;

    private EvaluationFrame(Map<String, ?> roots) {
        this.roots = Objects.requireNonNull(roots);
    }

    static EvaluationFrame of(Map<String, ?> roots) {
        return new EvaluationFrame(roots);
    }

    Set<String> rootNames() {
        return this.roots.keySet();
    }

    boolean hasBindings() {
        return this.bindingCount != 0;
    }

    boolean hasValue(SemanticAst.Reference reference) {
        if (!LAMBDA_ROOT.equals(reference.root())) {
            return this.roots.containsKey(reference.root());
        }
        return !reference.path().isEmpty() && this.bindingIndex(reference.path().getFirst()) >= 0;
    }

    void push(String name, Object value) {
        if (this.bindingCount == this.bindingNames.length) {
            int capacity = this.bindingCount == 0 ? INITIAL_BINDING_CAPACITY : this.bindingCount * 2;
            this.bindingNames = Arrays.copyOf(this.bindingNames, capacity);
            this.bindingValues = Arrays.copyOf(this.bindingValues, capacity);
        }
        this.bindingNames[this.bindingCount] = name;
        this.bindingValues[this.bindingCount] = value;
        this.bindingCount++;
    }

    void pop() {
        if (this.bindingCount == 0) {
            throw new IllegalStateException("evaluation binding stack is empty");
        }
        this.bindingCount--;
    }

    @Nullable
    Object read(SemanticAst.Reference reference) {
        Object current;
        int pathIndex;
        if (LAMBDA_ROOT.equals(reference.root())) {
            if (reference.path().isEmpty()) {
                throw failure("missing program value: " + reference.root(), reference.span());
            }
            int bindingIndex = this.bindingIndex(reference.path().getFirst());
            if (bindingIndex < 0) {
                throw failure("missing program value: " + display(reference), reference.span());
            }
            current = this.bindingValues[bindingIndex];
            pathIndex = 1;
        } else {
            if (!this.roots.containsKey(reference.root())) {
                throw failure("missing program root: " + reference.root(), reference.span());
            }
            current = this.roots.get(reference.root());
            pathIndex = 0;
        }

        List<String> path = reference.path();
        for (int index = pathIndex; index < path.size(); index++) {
            String name = path.get(index);
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(name)) {
                throw failure("missing program value: " + display(reference), reference.span());
            }
            current = map.get(name);
        }
        if (current == null) {
            if (reference.nullable() || reference.type() == LanguageType.Scalar.NULL) {
                return null;
            }
            throw failure("non-nullable program value is null", reference.span());
        }
        if (!EmbeddedLanguageEvaluator.matchesType(current, reference.type())) {
            throw failure("program input has an incompatible type", reference.span());
        }
        return current;
    }

    private int bindingIndex(String name) {
        for (int index = this.bindingCount - 1; index >= 0; index--) {
            if (name.equals(this.bindingNames[index])) {
                return index;
            }
        }
        return -1;
    }

    private static String display(SemanticAst.Reference reference) {
        return reference.root() + (reference.path().isEmpty() ? "" : "." + String.join(".", reference.path()));
    }

    private static EmbeddedLanguageException failure(String message, LanguageDiagnostic.SourceSpan span) {
        return new EmbeddedLanguageException(
            new LanguageDiagnostic(LanguageDiagnostic.Category.TypeError, message, span)
        );
    }
}
