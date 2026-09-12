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
@SuppressWarnings("checkstyle:NeedBraces")
final class EvaluationFrame {

    private static final String LAMBDA_ROOT = "__lambda__";
    private static final int INITIAL_BINDING_CAPACITY = 4;
    private static final String[] EMPTY_BINDING_NAMES = new String[0];
    private static final Object[] EMPTY_BINDING_VALUES = new Object[0];

    private final Map<String, ?> roots;
    private final @Nullable Object[] rootValues;
    private final byte[] rootStates;
    private final @Nullable Object[] localValues;
    private final boolean[] localPresent;
    private @Nullable String[] bindingNames = EMPTY_BINDING_NAMES;
    private @Nullable Object[] bindingValues = EMPTY_BINDING_VALUES;
    private int bindingCount;

    private EvaluationFrame(Map<String, ?> roots, int rootSlots, int localSlots) {
        this.roots = Objects.requireNonNull(roots);
        this.rootValues = new Object[rootSlots];
        this.rootStates = new byte[rootSlots];
        this.localValues = new Object[localSlots];
        this.localPresent = new boolean[localSlots];
    }

    static EvaluationFrame of(Map<String, ?> roots) {
        return new EvaluationFrame(roots, 0, 0);
    }

    static EvaluationFrame of(Map<String, ?> roots, int rootSlots, int localSlots) {
        return new EvaluationFrame(roots, rootSlots, localSlots);
    }

    Set<String> rootNames() {
        return this.roots.keySet();
    }

    boolean hasBindings() {
        if (this.bindingCount != 0) return true;
        for (boolean present : this.localPresent) {
            if (present) return true;
        }
        return false;
    }

    boolean hasValue(SemanticAst.Reference reference) {
        if (!LAMBDA_ROOT.equals(reference.root())) {
            return this.rootPresent(reference);
        }
        if (reference.localSlot() >= 0 && reference.localSlot() < this.localPresent.length) {
            return this.localPresent[reference.localSlot()];
        }
        return !reference.path().isEmpty() && this.bindingIndex(reference.path().getFirst()) >= 0;
    }

    void push(int slot, String name, @Nullable Object value) {
        if (slot >= 0 && slot < this.localValues.length) {
            this.localValues[slot] = value;
            this.localPresent[slot] = true;
            return;
        }
        if (this.bindingCount == this.bindingNames.length) {
            int capacity = this.bindingCount == 0 ? INITIAL_BINDING_CAPACITY : this.bindingCount * 2;
            this.bindingNames = Arrays.copyOf(this.bindingNames, capacity);
            this.bindingValues = Arrays.copyOf(this.bindingValues, capacity);
        }
        this.bindingNames[this.bindingCount] = name;
        this.bindingValues[this.bindingCount] = value;
        this.bindingCount++;
    }

    void pop(int slot) {
        if (slot >= 0 && slot < this.localValues.length) {
            this.localPresent[slot] = false;
            this.localValues[slot] = null;
            return;
        }
        if (this.bindingCount == 0) throw new IllegalStateException("evaluation binding stack is empty");
        this.bindingCount--;
        this.bindingNames[this.bindingCount] = null;
        this.bindingValues[this.bindingCount] = null;
    }

    @Nullable
    Object read(SemanticAst.Reference reference) {
        Object current;
        int pathIndex;
        if (LAMBDA_ROOT.equals(reference.root())) {
            if (reference.path().isEmpty()) {
                throw failure("missing program value: " + reference.root(), reference.span());
            }
            if (reference.localSlot() >= 0 && reference.localSlot() < this.localPresent.length) {
                if (!this.localPresent[reference.localSlot()]) {
                    throw failure("missing program value: " + display(reference), reference.span());
                }
                current = this.localValues[reference.localSlot()];
            } else {
                int bindingIndex = this.bindingIndex(reference.path().getFirst());
                if (bindingIndex < 0) {
                    throw failure("missing program value: " + display(reference), reference.span());
                }
                current = this.bindingValues[bindingIndex];
            }
            pathIndex = 1;
        } else {
            if (!this.rootPresent(reference)) {
                throw failure("missing program root: " + reference.root(), reference.span());
            }
            current = this.rootValue(reference);
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
            if (reference.nullable() || reference.type() == LanguageType.Scalar.NULL) return null;
            throw failure("non-nullable program value is null", reference.span());
        }
        if (!EmbeddedLanguageEvaluator.matchesType(current, reference.type())) {
            throw failure("program input has an incompatible type", reference.span());
        }
        return current;
    }

    private boolean rootPresent(SemanticAst.Reference reference) {
        int slot = reference.rootSlot();
        if (slot < 0 || slot >= this.rootStates.length) return this.roots.containsKey(reference.root());
        if (this.rootStates[slot] == 0) {
            if (this.roots.containsKey(reference.root())) {
                this.rootStates[slot] = 2;
                this.rootValues[slot] = this.roots.get(reference.root());
            } else {
                this.rootStates[slot] = 1;
            }
        }
        return this.rootStates[slot] == 2;
    }

    private @Nullable Object rootValue(SemanticAst.Reference reference) {
        int slot = reference.rootSlot();
        return slot >= 0 && slot < this.rootValues.length ? this.rootValues[slot] : this.roots.get(reference.root());
    }

    private int bindingIndex(String name) {
        for (int index = this.bindingCount - 1; index >= 0; index--) {
            if (name.equals(this.bindingNames[index])) return index;
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
