package io.taskmigo.language;

import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Stores root dependencies as a compact bit mask while retaining the public Set contract.
@SuppressWarnings("checkstyle:OverloadMethodsDeclarationOrder")
final class RootDependencies extends AbstractSet<String> {

    private static final long[] EMPTY_WORDS = new long[0];

    private final DependencyCatalog catalog;
    private final long mask;
    private final long[] words;

    private RootDependencies(DependencyCatalog catalog, long mask, long[] words) {
        this.catalog = catalog;
        this.mask = mask;
        this.words = words;
    }

    static RootDependencies empty(DependencyCatalog catalog) {
        return new RootDependencies(
            catalog,
            0L,
            catalog.size() > Long.SIZE ? new long[wordCount(catalog)] : EMPTY_WORDS
        );
    }

    static RootDependencies of(DependencyCatalog catalog, int slot) {
        if (catalog.size() <= Long.SIZE) {
            return new RootDependencies(catalog, 1L << slot, EMPTY_WORDS);
        }
        long[] words = new long[wordCount(catalog)];
        words[slot >>> 6] |= 1L << (slot & 63);
        return new RootDependencies(catalog, 0L, words);
    }

    static Set<String> union(Iterable<? extends SemanticAst.Expression> expressions) {
        RootDependencies compact = null;
        HashSet<String> fallback = null;
        for (SemanticAst.Expression expression : expressions) {
            Set<String> dependencies = expression.dependencies();
            if (fallback != null) {
                fallback.addAll(dependencies);
            } else if (dependencies instanceof RootDependencies roots) {
                if (compact == null) {
                    compact = roots;
                } else if (compact.catalog == roots.catalog) {
                    compact = compact.union(roots);
                } else {
                    fallback = new HashSet<>(compact);
                    fallback.addAll(roots);
                }
            } else if (!dependencies.isEmpty()) {
                fallback = compact == null ? new HashSet<>() : new HashSet<>(compact);
                fallback.addAll(dependencies);
            }
        }
        if (fallback != null) {
            return Set.copyOf(fallback);
        }
        return compact == null ? Set.of() : compact;
    }

    static Set<String> union(SemanticAst.Expression... expressions) {
        return union(List.of(expressions));
    }

    static boolean intersects(Set<String> dependencies, Set<String> roots) {
        if (dependencies.isEmpty() || roots.isEmpty()) {
            return false;
        }
        if (dependencies instanceof RootDependencies compact) {
            for (String root : roots) {
                if (compact.contains(root)) {
                    return true;
                }
            }
            return false;
        }
        for (String root : roots) {
            if (dependencies.contains(root)) {
                return true;
            }
        }
        return false;
    }

    RootDependencies union(RootDependencies other) {
        if (this.catalog != other.catalog) {
            throw new IllegalArgumentException("dependency catalogs do not match");
        }
        if (this.words.length == 0) {
            return new RootDependencies(this.catalog, this.mask | other.mask, EMPTY_WORDS);
        }
        long[] merged = this.words.clone();
        for (int index = 0; index < merged.length; index++) {
            merged[index] |= other.words[index];
        }
        return new RootDependencies(this.catalog, 0L, merged);
    }

    @Override
    public boolean contains(@Nullable Object value) {
        if (!(value instanceof String root)) {
            return false;
        }
        int slot = this.catalog.slot(root);
        return slot >= 0 && this.containsSlot(slot);
    }

    @Override
    public int size() {
        if (this.words.length == 0) {
            return Long.bitCount(this.mask);
        }
        int size = 0;
        for (long word : this.words) {
            size += Long.bitCount(word);
        }
        return size;
    }

    @Override
    public Iterator<String> iterator() {
        return new Iterator<>() {
            private int next = this.find(0);

            @Override
            public boolean hasNext() {
                return this.next >= 0;
            }

            @Override
            public String next() {
                if (this.next < 0) {
                    throw new NoSuchElementException();
                }
                int current = this.next;
                this.next = this.find(current + 1);
                return RootDependencies.this.catalog.root(current);
            }

            private int find(int start) {
                for (int slot = start; slot < RootDependencies.this.catalog.size(); slot++) {
                    if (RootDependencies.this.containsSlot(slot)) {
                        return slot;
                    }
                }
                return -1;
            }
        };
    }

    private boolean containsSlot(int slot) {
        if (this.words.length == 0) {
            return (this.mask & (1L << slot)) != 0L;
        }
        return (this.words[slot >>> 6] & (1L << (slot & 63))) != 0L;
    }

    private static int wordCount(DependencyCatalog catalog) {
        return (catalog.size() + Long.SIZE - 1) / Long.SIZE;
    }
}
