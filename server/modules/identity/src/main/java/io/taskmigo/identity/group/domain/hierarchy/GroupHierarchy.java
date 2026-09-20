package io.taskmigo.identity.group.domain.hierarchy;

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

/// Applies and validates the directed Group hierarchy independently of persistence.
public final class GroupHierarchy {

    private final ImmutableGraph<UUID> graph;

    private GroupHierarchy(Graph<UUID> graph) {
        this.graph = ImmutableGraph.copyOf(graph);
    }

    /// Creates a Group graph from persistence-neutral parent-to-child edges.
    public static GroupHierarchy from(Map<UUID, ? extends Collection<UUID>> childrenByParent) {
        MutableGraph<UUID> graph = GraphBuilder.directed().allowsSelfLoops(true).build();
        childrenByParent.forEach((parent, children) -> {
            graph.addNode(parent);
            children.forEach(child -> graph.putEdge(parent, child));
        });
        return new GroupHierarchy(graph);
    }

    /// Returns whether the Group exists in this hierarchy snapshot.
    public boolean contains(UUID groupId) {
        return this.graph.nodes().contains(groupId);
    }

    /// Returns whether every requested Group exists in this hierarchy snapshot.
    public boolean containsAll(Collection<UUID> groupIds) {
        return this.graph.nodes().containsAll(groupIds);
    }

    /// Returns every Group in deterministic identifier order.
    public List<UUID> groupIds() {
        return this.graph.nodes().stream().sorted().toList();
    }

    /// Returns a validated graph with one Group's direct children replaced.
    public GroupHierarchy replacingChildren(UUID parent, Collection<UUID> children) {
        MutableGraph<UUID> replaced = Graphs.copyOf(this.graph);
        replaced.addNode(parent);
        Set.copyOf(replaced.successors(parent)).forEach(child -> replaced.removeEdge(parent, child));
        children.forEach(child -> replaced.putEdge(parent, child));

        GroupHierarchy candidate = new GroupHierarchy(replaced);
        candidate.requireAcyclic();
        return candidate;
    }

    /// Returns a graph with one Group and all incident hierarchy edges removed.
    public GroupHierarchy removing(UUID groupId) {
        MutableGraph<UUID> remaining = Graphs.copyOf(this.graph);
        remaining.removeNode(groupId);
        return new GroupHierarchy(remaining);
    }

    /// Returns every reachable Group from one root in deterministic identifier order.
    public List<UUID> reachableFrom(UUID root) {
        return this.reachableFrom(List.of(root));
    }

    /// Returns every reachable Group from the supplied roots in deterministic identifier order.
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
            throw new GroupHierarchyException("Group hierarchy must be acyclic");
        }
    }
}
