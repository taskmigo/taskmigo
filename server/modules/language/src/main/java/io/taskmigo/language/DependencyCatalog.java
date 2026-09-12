package io.taskmigo.language;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Assigns stable compact slots to one Environment Schema's roots.
final class DependencyCatalog {

    private final List<String> roots;
    private final Map<String, Integer> slots;
    private final RootDependencies empty;

    DependencyCatalog(Collection<String> rootNames) {
        ArrayList<String> ordered = new ArrayList<>(rootNames);
        ordered.sort(String::compareTo);
        this.roots = List.copyOf(ordered);
        HashMap<String, Integer> indexed = new HashMap<>();
        for (int index = 0; index < this.roots.size(); index++) {
            indexed.put(this.roots.get(index), index);
        }
        this.slots = Map.copyOf(indexed);
        this.empty = RootDependencies.empty(this);
    }

    int size() {
        return this.roots.size();
    }

    String root(int slot) {
        return this.roots.get(slot);
    }

    int slot(String root) {
        Integer slot = this.slots.get(root);
        return slot == null ? -1 : slot;
    }

    RootDependencies empty() {
        return this.empty;
    }

    RootDependencies dependency(String root) {
        int slot = this.slot(root);
        if (slot < 0) {
            throw new IllegalArgumentException("unknown schema root: " + root);
        }
        return RootDependencies.of(this, slot);
    }
}
