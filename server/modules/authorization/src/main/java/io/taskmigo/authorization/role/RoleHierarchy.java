package io.taskmigo.authorization.role;

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

/// Applies and validates the directed role-inheritance graph independently of persistence.
public final class RoleHierarchy {

    private final ImmutableGraph<UUID> graph;

    RoleHierarchy(Map<UUID, ? extends Collection<UUID>> childrenByParent) {
        MutableGraph<UUID> graph = GraphBuilder.directed().allowsSelfLoops(true).build();

        childrenByParent.forEach((parent, children) -> {
            graph.addNode(parent);
            children.forEach(child -> graph.putEdge(parent, child));
        });

        this.graph = ImmutableGraph.copyOf(graph);
    }

    private RoleHierarchy(Graph<UUID> graph) {
        this.graph = ImmutableGraph.copyOf(graph);
    }

    /// Creates a role graph from persistence-neutral parent-to-child edges.
    public static RoleHierarchy from(Map<UUID, ? extends Collection<UUID>> childrenByParent) {
        return new RoleHierarchy(childrenByParent);
    }

    /// Returns a validated graph with one parent's direct children replaced.
    public RoleHierarchy replacingChildren(UUID parent, Collection<UUID> children) {
        MutableGraph<UUID> replaced = Graphs.copyOf(this.graph);
        replaced.addNode(parent);
        Set.copyOf(replaced.successors(parent)).forEach(child -> replaced.removeEdge(parent, child));
        children.forEach(child -> replaced.putEdge(parent, child));

        RoleHierarchy candidate = new RoleHierarchy(replaced);
        candidate.requireAcyclic();
        return candidate;
    }

    /// Returns every reachable descendant of a root in deterministic order.
    public List<UUID> descendants(UUID root) {
        if (!this.graph.nodes().contains(root)) {
            return List.of();
        }
        return this.reachableFrom(List.of(root))
            .stream()
            .filter(role -> !role.equals(root))
            .toList();
    }

    public List<UUID> reachableFrom(Collection<UUID> roots) {
        return StreamSupport.stream(Traverser.forGraph(this.graph).breadthFirst(roots).spliterator(), false)
            .sorted()
            .toList();
    }

    private void requireAcyclic() {
        if (Graphs.hasCycle(this.graph)) {
            throw new RoleHierarchyException("Role hierarchy must be acyclic");
        }
    }
}
