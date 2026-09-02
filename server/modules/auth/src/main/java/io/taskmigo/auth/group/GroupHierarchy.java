package io.taskmigo.auth.group;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

public final class GroupHierarchy {

    private final ImmutableGraph<UUID> graph;

    GroupHierarchy(Map<UUID, ? extends Collection<UUID>> childrenByParent) {
        MutableGraph<UUID> graph = newGraph();
        childrenByParent.forEach((parent, children) -> {
            graph.addNode(parent);
            children.forEach(child -> graph.putEdge(parent, child));
        });
        this.graph = ImmutableGraph.copyOf(graph);
    }

    private GroupHierarchy(Graph<UUID> graph) {
        this.graph = ImmutableGraph.copyOf(graph);
    }

    public static GroupHierarchy from(Collection<GroupEntity> groups) {
        Map<UUID, Set<UUID>> childrenByParent = new HashMap<>();
        for (GroupEntity group : groups) {
            Set<UUID> children = new HashSet<>();
            for (GroupEntity child : group.childGroups) children.add(child.id);
            childrenByParent.put(group.id, children);
        }
        return new GroupHierarchy(childrenByParent);
    }

    GroupHierarchy replacingChildren(UUID parent, Collection<UUID> children) {
        MutableGraph<UUID> replaced = Graphs.copyOf(this.graph);
        replaced.addNode(parent);
        Set.copyOf(replaced.successors(parent)).forEach(child -> replaced.removeEdge(parent, child));
        children.forEach(child -> replaced.putEdge(parent, child));

        GroupHierarchy candidate = new GroupHierarchy(replaced);
        candidate.requireAcyclic();
        return candidate;
    }

    List<UUID> reachableFrom(UUID root) {
        return this.reachableFrom(List.of(root));
    }

    public List<UUID> reachableFrom(Collection<UUID> roots) {
        Set<UUID> knownRoots = roots
            .stream()
            .filter(this.graph.nodes()::contains)
            .collect(java.util.stream.Collectors.toSet());
        if (knownRoots.isEmpty()) return List.of();
        return StreamSupport.stream(Traverser.forGraph(this.graph).breadthFirst(knownRoots).spliterator(), false)
            .sorted()
            .toList();
    }

    private void requireAcyclic() {
        if (Graphs.hasCycle(this.graph)) throw new GroupException(
            GroupException.Type.BAD_REQUEST,
            "Group hierarchy must be acyclic"
        );
    }

    private static MutableGraph<UUID> newGraph() {
        return GraphBuilder.directed().allowsSelfLoops(true).build();
    }
}
