package io.taskmigo.auth.role;

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

    public static RoleHierarchy from(Collection<RoleEntity> roles) {
        Map<UUID, Set<UUID>> childrenByParent = new HashMap<>();
        for (RoleEntity role : roles) {
            Set<UUID> children = new HashSet<>();
            for (RoleEntity child : role.childRoles) children.add(child.id);
            childrenByParent.put(role.id, children);
        }
        return new RoleHierarchy(childrenByParent);
    }

    RoleHierarchy replacingChildren(UUID parent, Collection<UUID> children) {
        MutableGraph<UUID> replaced = Graphs.copyOf(this.graph);
        replaced.addNode(parent);
        Set.copyOf(replaced.successors(parent)).forEach(child -> replaced.removeEdge(parent, child));
        children.forEach(child -> replaced.putEdge(parent, child));

        RoleHierarchy candidate = new RoleHierarchy(replaced);
        candidate.requireAcyclic();
        return candidate;
    }

    List<UUID> descendants(UUID root) {
        if (!this.graph.nodes().contains(root)) return List.of();
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
        if (Graphs.hasCycle(this.graph)) throw new RoleException(
            RoleException.Type.BAD_REQUEST,
            "Role hierarchy must be acyclic"
        );
    }
}
