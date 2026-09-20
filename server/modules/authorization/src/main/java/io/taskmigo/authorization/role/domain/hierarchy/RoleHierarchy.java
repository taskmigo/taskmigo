package io.taskmigo.authorization.role.domain.hierarchy;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

/// Applies and validates the cross-aggregate directed Role hierarchy independently of persistence.
public final class RoleHierarchy {

    private final ImmutableGraph<UUID> graph;

    private RoleHierarchy(Graph<UUID> graph) {
        this.graph = ImmutableGraph.copyOf(graph);
    }

    /// Creates a Role graph from persistence-neutral parent-to-child edges.
    public static RoleHierarchy from(Map<UUID, ? extends Collection<UUID>> childrenByParent) {
        MutableGraph<UUID> graph = GraphBuilder.directed().allowsSelfLoops(true).build();
        childrenByParent.forEach((parent, children) -> {
            graph.addNode(parent);
            children.forEach(child -> graph.putEdge(parent, child));
        });
        return new RoleHierarchy(graph);
    }

    /// Returns whether the Role exists in this hierarchy snapshot.
    public boolean contains(UUID roleId) {
        return this.graph.nodes().contains(roleId);
    }

    /// Returns whether every requested Role exists in this hierarchy snapshot.
    public boolean containsAll(Collection<UUID> roleIds) {
        return this.graph.nodes().containsAll(roleIds);
    }

    /// Returns every Role in deterministic identifier order.
    public List<UUID> roleIds() {
        return this.graph.nodes().stream().sorted().toList();
    }

    /// Returns a validated graph with one Role's direct children replaced.
    public RoleHierarchy replacingChildren(UUID parent, Collection<UUID> children) {
        MutableGraph<UUID> replaced = Graphs.copyOf(this.graph);
        replaced.addNode(parent);
        Set.copyOf(replaced.successors(parent)).forEach(child -> replaced.removeEdge(parent, child));
        children.forEach(child -> replaced.putEdge(parent, child));

        RoleHierarchy candidate = new RoleHierarchy(replaced);
        candidate.requireAcyclic();
        return candidate;
    }

    /// Returns a graph with one Role and all incident hierarchy edges removed.
    public RoleHierarchy removing(UUID roleId) {
        MutableGraph<UUID> remaining = Graphs.copyOf(this.graph);
        remaining.removeNode(roleId);
        return new RoleHierarchy(remaining);
    }

    /// Returns every reachable descendant of a root in deterministic identifier order.
    public List<UUID> descendants(UUID root) {
        return this.reachableFrom(List.of(root))
            .stream()
            .filter(role -> !role.equals(root))
            .toList();
    }

    /// Returns every reachable Role from the supplied roots in deterministic identifier order.
    public List<UUID> reachableFrom(Collection<UUID> roots) {
        List<UUID> knownRoots = roots.stream().filter(this.graph.nodes()::contains).distinct().sorted().toList();
        if (knownRoots.isEmpty()) {
            return List.of();
        }
        return StreamSupport.stream(Traverser.forGraph(this.graph).breadthFirst(knownRoots).spliterator(), false)
            .sorted()
            .toList();
    }

    private void requireAcyclic() {
        if (Graphs.hasCycle(this.graph)) {
            throw new RoleHierarchyException("Role hierarchy must be acyclic");
        }
    }
}
