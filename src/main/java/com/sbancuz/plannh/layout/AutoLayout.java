package com.sbancuz.plannh.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.elk.alg.layered.LayeredLayoutProvider;
import org.eclipse.elk.alg.layered.options.CrossingMinimizationStrategy;
import org.eclipse.elk.alg.layered.options.CycleBreakingStrategy;
import org.eclipse.elk.alg.layered.options.GraphCompactionStrategy;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.alg.layered.options.LayeringStrategy;
import org.eclipse.elk.alg.layered.options.NodePlacementStrategy;
import org.eclipse.elk.alg.layered.options.OrderingStrategy;
import org.eclipse.elk.core.data.LayoutMetaDataService;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.options.PortConstraints;
import org.eclipse.elk.core.options.PortSide;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.ElkPort;
import org.eclipse.elk.graph.util.ElkGraphUtil;

import com.sbancuz.plannh.gui.PortGeometry;

/**
 * Deterministic layered auto-layout over the machine digraph.
 *
 * <p>
 * Uses ELK layered with the option set tuned in the flowv2 research corpus: DEPTH_FIRST cycle
 * breaking (halves crossings on recycle-heavy charts vs the GREEDY default), NETWORK_SIMPLEX
 * layering, thoroughness 30 and post-compaction. NETWORK_SIMPLEX node placement blows up past a
 * few hundred nodes, so large graphs fall back to BRANDES_KOEPF.
 *
 * <p>
 * Layered engines consume nodes in model order, so the same chart built in a different NEI click
 * order would lay out differently. Nodes are therefore canonicalized first: SCC condensation
 * height, then name, then id. Same graph, same layout, always.
 *
 * <p>
 * This class is deliberately free of Minecraft/NEI imports so it can be exercised headlessly.
 */
public final class AutoLayout {

    /** World-space size and port counts of one machine node. */
    public record NodeBox(UUID id, String name, int width, int height, int inputPorts, int outputPorts) {}

    /** One drawn edge: source node output port -> target node input port. */
    public record Link(UUID source, int outputIndex, UUID target, int inputIndex) {}

    /** Nodes above this count switch to the scalable placement strategy. */
    private static final int BIG_GRAPH_NODES = 300;
    // Compact on purpose. The arrow router inflates nodes by a 12-unit margin per side, so
    // layer spacing must stay above ~30 or the inter-layer corridors close entirely.
    private static final double LAYER_SPACING = 50.0;
    private static final double NODE_SPACING = 25.0;
    // Per-edge channel width ELK reserves in a layer gap (the router uses 6-unit cells and
    // keeps a cell of clearance between arrows, so ~2 cells per arrow), and the clearance
    // between those channels and the nodes flanking the gap - generous so downstream nodes
    // sit clear of descending edges and port approaches stay straight.
    private static final double EDGE_CHANNEL_SPACING = 18.0;
    private static final double EDGE_NODE_SPACING = 24.0;

    static {
        // We invoke LayeredLayoutProvider directly instead of going through ELK's plugin/service
        // machinery, so the property clone registry must be initialized by hand.
        LayoutMetaDataService.initElkReflect();
    }

    private AutoLayout() {}

    /**
     * Computes new world-space top-left positions for every node. Positions are relative to an
     * arbitrary origin; callers anchor the result wherever they want.
     */
    public static Map<UUID, int[]> layout(final List<NodeBox> nodes, final List<Link> links) {
        final Map<UUID, int[]> result = new HashMap<>();
        if (nodes.isEmpty()) return result;

        final List<NodeBox> ordered = canonicalOrder(nodes, links);

        final ElkNode root = ElkGraphUtil.createGraph();
        root.setProperty(CoreOptions.DIRECTION, Direction.RIGHT);
        root.setProperty(CoreOptions.RANDOM_SEED, 1);
        root.setProperty(CoreOptions.SPACING_NODE_NODE, NODE_SPACING);
        root.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, LAYER_SPACING);
        // Orthogonal edge routing makes ELK reserve a channel per edge crossing each layer
        // gap, so gaps widen with edge count instead of every corridor being LAYER_SPACING.
        root.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);
        root.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, EDGE_CHANNEL_SPACING);
        root.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, EDGE_NODE_SPACING);
        root.setProperty(LayeredOptions.CROSSING_MINIMIZATION_STRATEGY, CrossingMinimizationStrategy.LAYER_SWEEP);
        root.setProperty(LayeredOptions.CYCLE_BREAKING_STRATEGY, CycleBreakingStrategy.DEPTH_FIRST);
        root.setProperty(LayeredOptions.THOROUGHNESS, 30);
        root.setProperty(LayeredOptions.LAYERING_STRATEGY, LayeringStrategy.NETWORK_SIMPLEX);
        root.setProperty(
            LayeredOptions.NODE_PLACEMENT_STRATEGY,
            nodes.size() <= BIG_GRAPH_NODES ? NodePlacementStrategy.NETWORK_SIMPLEX
                : NodePlacementStrategy.BRANDES_KOEPF);
        root.setProperty(
            LayeredOptions.COMPACTION_POST_COMPACTION_STRATEGY,
            GraphCompactionStrategy.LEFT_RIGHT_CONSTRAINT_LOCKING);
        if (nodes.size() > BIG_GRAPH_NODES) {
            root.setProperty(LayeredOptions.CONSIDER_MODEL_ORDER_STRATEGY, OrderingStrategy.NODES_AND_EDGES);
        }

        final Map<UUID, ElkNode> elkNodes = new HashMap<>();
        final Map<UUID, ElkPort[]> inPorts = new HashMap<>();
        final Map<UUID, ElkPort[]> outPorts = new HashMap<>();

        for (final NodeBox box : ordered) {
            final ElkNode n = ElkGraphUtil.createNode(root);
            n.setWidth(box.width());
            n.setHeight(box.height());
            // Real pin coordinates, not just sides: node placement then straightens edges
            // against the positions the canvas actually draws, so single connections line up
            // horizontally instead of jogging by ELK's invented port spread.
            n.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS);
            elkNodes.put(box.id(), n);

            final ElkPort[] outs = new ElkPort[box.outputPorts()];
            for (int i = 0; i < box.outputPorts(); i++) {
                final ElkPort p = ElkGraphUtil.createPort(n);
                p.setProperty(CoreOptions.PORT_SIDE, PortSide.EAST);
                p.setX(box.width());
                p.setY(PortGeometry.portY(i));
                outs[i] = p;
            }
            outPorts.put(box.id(), outs);

            final ElkPort[] ins = new ElkPort[box.inputPorts()];
            for (int i = 0; i < box.inputPorts(); i++) {
                final ElkPort p = ElkGraphUtil.createPort(n);
                p.setProperty(CoreOptions.PORT_SIDE, PortSide.WEST);
                p.setX(0);
                p.setY(PortGeometry.portY(i));
                ins[i] = p;
            }
            inPorts.put(box.id(), ins);
        }

        for (final Link link : links) {
            final ElkPort[] outs = outPorts.get(link.source());
            final ElkPort[] ins = inPorts.get(link.target());
            if (outs == null || ins == null) continue;
            if (link.outputIndex() < 0 || link.outputIndex() >= outs.length) continue;
            if (link.inputIndex() < 0 || link.inputIndex() >= ins.length) continue;
            ElkGraphUtil.createSimpleEdge(outs[link.outputIndex()], ins[link.inputIndex()]);
        }

        new LayeredLayoutProvider().layout(root, new BasicProgressMonitor());

        for (final Map.Entry<UUID, ElkNode> entry : elkNodes.entrySet()) {
            final ElkNode n = entry.getValue();
            result.put(entry.getKey(), new int[] { (int) Math.round(n.getX()), (int) Math.round(n.getY()) });
        }
        return result;
    }

    /**
     * Orders nodes by SCC-condensation height (sources first), then name, then id, so the layout
     * is independent of the order recipes were added in NEI.
     */
    static List<NodeBox> canonicalOrder(final List<NodeBox> nodes, final List<Link> links) {
        final Map<UUID, List<UUID>> adjacency = new HashMap<>();
        for (final NodeBox box : nodes) adjacency.put(box.id(), new ArrayList<>());
        for (final Link link : links) {
            final List<UUID> next = adjacency.get(link.source());
            if (next != null && adjacency.containsKey(link.target())) next.add(link.target());
        }

        final Map<UUID, Integer> sccOf = tarjanScc(adjacency);

        // Condensation edges, then longest-path height per SCC from the source side.
        final Map<Integer, List<Integer>> sccAdjacency = new HashMap<>();
        final Map<Integer, Integer> inDegree = new HashMap<>();
        for (final int scc : sccOf.values()) {
            sccAdjacency.putIfAbsent(scc, new ArrayList<>());
            inDegree.putIfAbsent(scc, 0);
        }
        for (final Link link : links) {
            final Integer a = sccOf.get(link.source());
            final Integer b = sccOf.get(link.target());
            if (a == null || b == null || a.equals(b)) continue;
            sccAdjacency.get(a)
                .add(b);
            inDegree.merge(b, 1, Integer::sum);
        }

        final Map<Integer, Integer> height = new HashMap<>();
        final Deque<Integer> queue = new ArrayDeque<>();
        for (final Map.Entry<Integer, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                height.put(entry.getKey(), 0);
                queue.add(entry.getKey());
            }
        }
        final Map<Integer, Integer> remaining = new HashMap<>(inDegree);
        while (!queue.isEmpty()) {
            final int scc = queue.poll();
            for (final int next : sccAdjacency.get(scc)) {
                height.merge(next, height.get(scc) + 1, Math::max);
                if (remaining.merge(next, -1, Integer::sum) == 0) {
                    queue.add(next);
                }
            }
        }

        final List<NodeBox> ordered = new ArrayList<>(nodes);
        ordered.sort(
            Comparator.<NodeBox>comparingInt(box -> height.getOrDefault(sccOf.get(box.id()), 0))
                .thenComparing(box -> box.name() == null ? "" : box.name())
                .thenComparing(
                    box -> box.id()
                        .toString()));
        return ordered;
    }

    /** Iterative Tarjan; returns SCC index per node. */
    private static Map<UUID, Integer> tarjanScc(final Map<UUID, List<UUID>> adjacency) {
        final Map<UUID, Integer> index = new HashMap<>();
        final Map<UUID, Integer> lowLink = new HashMap<>();
        final Map<UUID, Boolean> onStack = new HashMap<>();
        final Deque<UUID> stack = new ArrayDeque<>();
        final Map<UUID, Integer> sccOf = new HashMap<>();
        final int[] counters = new int[2]; // next index, next scc id

        for (final UUID start : adjacency.keySet()) {
            if (index.containsKey(start)) continue;

            // Explicit DFS stack: node + position in its adjacency list.
            final Deque<UUID> dfs = new ArrayDeque<>();
            final Deque<Integer> edgePos = new ArrayDeque<>();
            dfs.push(start);
            edgePos.push(0);
            index.put(start, counters[0]);
            lowLink.put(start, counters[0]);
            counters[0]++;
            stack.push(start);
            onStack.put(start, true);

            while (!dfs.isEmpty()) {
                final UUID node = dfs.peek();
                final int pos = edgePos.peek();
                final List<UUID> next = adjacency.get(node);

                if (pos < next.size()) {
                    edgePos.push(edgePos.pop() + 1);
                    final UUID target = next.get(pos);
                    if (!index.containsKey(target)) {
                        dfs.push(target);
                        edgePos.push(0);
                        index.put(target, counters[0]);
                        lowLink.put(target, counters[0]);
                        counters[0]++;
                        stack.push(target);
                        onStack.put(target, true);
                    } else if (Boolean.TRUE.equals(onStack.get(target))) {
                        lowLink.merge(node, index.get(target), Math::min);
                    }
                } else {
                    dfs.pop();
                    edgePos.pop();
                    if (!dfs.isEmpty()) {
                        lowLink.merge(dfs.peek(), lowLink.get(node), Math::min);
                    }
                    if (lowLink.get(node)
                        .equals(index.get(node))) {
                        UUID member;
                        do {
                            member = stack.pop();
                            onStack.put(member, false);
                            sccOf.put(member, counters[1]);
                        } while (!member.equals(node));
                        counters[1]++;
                    }
                }
            }
        }
        return sccOf;
    }
}
