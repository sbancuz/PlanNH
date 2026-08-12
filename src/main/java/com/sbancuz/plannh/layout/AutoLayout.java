package com.sbancuz.plannh.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
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

import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.gui.PortGeometry;

/**
 * Deterministic layered auto-layout over the machine digraph.
 *
 * <p>
 * Uses ELK layered with an option set tuned on real recipe charts: DEPTH_FIRST cycle
 * breaking (halves crossings on recycle-heavy charts vs the GREEDY default), NETWORK_SIMPLEX
 * layering, thoroughness 30 and post-compaction. NETWORK_SIMPLEX node placement costs grow
 * explosively with edge density, so anything past a small chart falls back to BRANDES_KOEPF.
 *
 * <p>
 * Layered engines consume nodes in model order, so the same chart built in a different NEI click
 * order would lay out differently. Nodes are therefore canonicalized first: SCC condensation
 * height, then name, then wiring, then id - and links follow the node order. Same graph, same
 * layout, always.
 *
 * <p>
 * This class is deliberately free of Minecraft/NEI imports so it can be exercised headlessly.
 */
public final class AutoLayout {

    /**
     * Read view of one machine node: identity, world-space size, and port counts. The GUI's node
     * widget implements this directly, so layout consumes the existing objects instead of a
     * copied model, while this class stays free of Minecraft imports.
     */
    public interface LayoutNode {

        UUID id();

        String machineName();

        int worldWidth();

        int worldHeight();

        int inputCount();

        int outputCount();
    }

    /** Above this count ELK's model-order tie-breaking is enabled to keep big charts stable. */
    private static final int BIG_GRAPH_NODES = 300;
    // NETWORK_SIMPLEX placement is worth its cost only on small charts. Measured on chain +
    // 65% random cross links, 4 ports per node: 60 nodes/97 edges 0.4s, 80/130 1.0s, 100/163
    // 4.6s, 150/245 34s, and 200/327 dies in ELK's recursive NGraph.dfs with a StackOverflowError.
    // BRANDES_KOEPF is flat over the same range (0.2s at 60, 0.6s at 150, 3.5s at 320).
    private static final int NS_MAX_NODES = 60;
    private static final int NS_MAX_EDGES = 120;
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

    /** Guards the one-time ELK warm-up. */
    private static boolean warmedUp;

    /**
     * Runs one tiny layout so ELK's class loading and metadata registration happen off the
     * first real click. Failures are ignored - the first real layout would surface them anyway.
     */
    public static void warmup() {
        if (warmedUp) return;
        record WarmupNode(UUID id, String machineName, int worldWidth, int worldHeight, int inputCount, int outputCount)
            implements LayoutNode {}
        try {
            final UUID a = new UUID(0, 1);
            final UUID b = new UUID(0, 2);
            layout(
                List.of(new WarmupNode(a, "a", 100, 80, 0, 1), new WarmupNode(b, "b", 100, 80, 1, 0)),
                List.of(new Edge(new UUID(0, 3), a, b, 0, 0)));
            warmedUp = true;
        } catch (final Exception ignored) {}
    }

    /**
     * Computes new world-space top-left positions for every node. Positions are relative to an
     * arbitrary origin; callers anchor the result wherever they want.
     */
    public static Map<UUID, int[]> layout(final Collection<? extends LayoutNode> nodes, final Collection<Edge> links) {
        return layout(nodes, links, Map.of());
    }

    /**
     * @param margins world-space {@code {left, right}} to keep clear beside each node, by node id.
     *                The boundary chips hang off a node's pins and are not nodes themselves, so
     *                without this the layout packs a neighbour exactly where the label goes and the
     *                two overlap. Absent ids get no margin.
     */
    public static Map<UUID, int[]> layout(final Collection<? extends LayoutNode> nodes, final Collection<Edge> links,
        final Map<UUID, int[]> margins) {
        try {
            return layout(nodes, links, margins, GraphCompactionStrategy.LEFT_RIGHT_CONSTRAINT_LOCKING);
        } catch (final IllegalStateException e) {
            // "Invalid hitboxes for scanline constraint calculation" - ELK's post-compaction pass
            // rejects its own hitboxes on roughly 1 chart in 400 (measured: 3 of 1200 random
            // charts, 18-40 nodes). It is a property of the chart, not a race, so an affected
            // chart fails every time and this fallback is as deterministic as the primary path.
            // Dropping the pass costs 3-20% width and no height. A second failure would be a
            // different bug, so the retry runs unguarded.
            return layout(nodes, links, margins, GraphCompactionStrategy.NONE);
        }
    }

    /**
     * {@code {left, right}} padding for a node, or zeroes when it has no labels hanging off it.
     *
     * <p>
     * The caller asks for the clearance a label needs; what gets added is that minus the corridor
     * the layout was going to leave anyway. Without the credit both nodes either side of a gap pay
     * the full width of their own label on top of {@link #LAYER_SPACING}, and a chart of long
     * ingredient names ends up with columns hundreds of units further apart than anything in them
     * needs.
     */
    private static int[] padOf(final Map<UUID, int[]> margins, final UUID id) {
        final int[] margin = margins.get(id);
        if (margin == null) return EMPTY_PAD;
        return new int[] { credited(margin[0]), credited(margin[1]) };
    }

    private static int credited(final int requested) {
        return requested <= 0 ? 0 : Math.max(0, requested - (int) (LAYER_SPACING / 2));
    }

    private static final int[] EMPTY_PAD = { 0, 0 };

    private static Map<UUID, int[]> layout(final Collection<? extends LayoutNode> nodes, final Collection<Edge> links,
        final Map<UUID, int[]> margins, final GraphCompactionStrategy compaction) {
        final Map<UUID, int[]> result = new HashMap<>();
        if (nodes.isEmpty()) return result;

        final List<LayoutNode> ordered = canonicalOrder(nodes, links);

        // ELK consumes edges in model order too, so links follow the node canonicalization:
        // source position, then port, then target position.
        final Map<UUID, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            orderIndex.put(
                ordered.get(i)
                    .id(),
                i);
        }
        final List<Edge> orderedLinks = new ArrayList<>(links);
        orderedLinks.sort(
            Comparator.<Edge>comparingInt(link -> orderIndex.getOrDefault(link.sourceNodeId, -1))
                .thenComparingInt(link -> link.sourceOutputIndex)
                .thenComparingInt(link -> orderIndex.getOrDefault(link.targetNodeId, -1))
                .thenComparingInt(link -> link.targetInputIndex));

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
            nodes.size() <= NS_MAX_NODES && links.size() <= NS_MAX_EDGES ? NodePlacementStrategy.NETWORK_SIMPLEX
                : NodePlacementStrategy.BRANDES_KOEPF);
        root.setProperty(LayeredOptions.COMPACTION_POST_COMPACTION_STRATEGY, compaction);
        if (nodes.size() > BIG_GRAPH_NODES) {
            root.setProperty(LayeredOptions.CONSIDER_MODEL_ORDER_STRATEGY, OrderingStrategy.NODES_AND_EDGES);
        }

        final Map<UUID, ElkNode> elkNodes = new HashMap<>();
        final Map<UUID, ElkPort[]> inPorts = new HashMap<>();
        final Map<UUID, ElkPort[]> outPorts = new HashMap<>();

        for (final LayoutNode node : ordered) {
            final ElkNode n = ElkGraphUtil.createNode(root);
            // Space for the boundary labels is baked into the box rather than declared as a node
            // margin: ELK layered recomputes MARGINS during placement for its own labels and ports,
            // so an input margin is silently discarded and the next column lands on top of the
            // text. A wider box cannot be ignored. The padding is taken back off the result below,
            // and the pins shift with it so edges still meet the node where it is really drawn.
            final int[] pad = padOf(margins, node.id());
            n.setWidth(node.worldWidth() + pad[0] + pad[1]);
            n.setHeight(node.worldHeight());
            // Real pin coordinates, not just sides: node placement then straightens edges
            // against the positions the canvas actually draws, so single connections line up
            // horizontally instead of jogging by ELK's invented port spread.
            n.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS);
            elkNodes.put(node.id(), n);

            final ElkPort[] outs = new ElkPort[node.outputCount()];
            for (int i = 0; i < node.outputCount(); i++) {
                final ElkPort p = ElkGraphUtil.createPort(n);
                p.setProperty(CoreOptions.PORT_SIDE, PortSide.EAST);
                p.setX(pad[0] + node.worldWidth());
                p.setY(PortGeometry.portY(i));
                outs[i] = p;
            }
            outPorts.put(node.id(), outs);

            final ElkPort[] ins = new ElkPort[node.inputCount()];
            for (int i = 0; i < node.inputCount(); i++) {
                final ElkPort p = ElkGraphUtil.createPort(n);
                p.setProperty(CoreOptions.PORT_SIDE, PortSide.WEST);
                p.setX(pad[0]);
                p.setY(PortGeometry.portY(i));
                ins[i] = p;
            }
            inPorts.put(node.id(), ins);
        }

        for (final Edge link : orderedLinks) {
            final ElkPort[] outs = outPorts.get(link.sourceNodeId);
            final ElkPort[] ins = inPorts.get(link.targetNodeId);
            if (outs == null || ins == null) continue;
            if (link.sourceOutputIndex < 0 || link.sourceOutputIndex >= outs.length) continue;
            if (link.targetInputIndex < 0 || link.targetInputIndex >= ins.length) continue;
            ElkGraphUtil.createSimpleEdge(outs[link.sourceOutputIndex], ins[link.targetInputIndex]);
        }

        new LayeredLayoutProvider().layout(root, new BasicProgressMonitor());

        for (final Map.Entry<UUID, ElkNode> entry : elkNodes.entrySet()) {
            final ElkNode n = entry.getValue();
            // Undo the left padding: the caller positions the node, not the node-plus-its-labels.
            final int left = padOf(margins, entry.getKey())[0];
            result.put(entry.getKey(), new int[] { (int) Math.round(n.getX()) + left, (int) Math.round(n.getY()) });
        }
        return result;
    }

    /**
     * Orders nodes by SCC-condensation height (sources first), then name, then wiring color,
     * then id, so the layout is independent of the order recipes were added in NEI.
     */
    static List<LayoutNode> canonicalOrder(final Collection<? extends LayoutNode> nodes, final Collection<Edge> links) {
        final Map<UUID, List<UUID>> adjacency = new HashMap<>();
        for (final LayoutNode node : nodes) adjacency.put(node.id(), new ArrayList<>());
        for (final Edge link : links) {
            final List<UUID> next = adjacency.get(link.sourceNodeId);
            if (next != null && adjacency.containsKey(link.targetNodeId)) next.add(link.targetNodeId);
        }

        final Map<UUID, Integer> sccOf = tarjanScc(adjacency);

        // Condensation edges, then longest-path height per SCC from the source side.
        final Map<Integer, List<Integer>> sccAdjacency = new HashMap<>();
        final Map<Integer, Integer> inDegree = new HashMap<>();
        for (final int scc : sccOf.values()) {
            sccAdjacency.putIfAbsent(scc, new ArrayList<>());
            inDegree.putIfAbsent(scc, 0);
        }
        for (final Edge link : links) {
            final Integer a = sccOf.get(link.sourceNodeId);
            final Integer b = sccOf.get(link.targetNodeId);
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

        final Map<UUID, Integer> nodeHeight = new HashMap<>();
        for (final LayoutNode node : nodes) {
            nodeHeight.put(node.id(), height.getOrDefault(sccOf.get(node.id()), 0));
        }

        // Ids are random at creation, so an id tiebreak alone would let two same-name machines at
        // the same height swap between rebuilds of the same chart. Tie-breaking on wiring colors
        // first leaves the id ordering only true structural twins, and swapping twins does not
        // change the picture.
        final Map<UUID, String> names = new HashMap<>();
        for (final LayoutNode node : nodes) {
            names.put(node.id(), node.machineName() == null ? "" : node.machineName());
        }
        final Map<UUID, Integer> color = wiringColors(nodes, links, names, nodeHeight);

        final List<LayoutNode> ordered = new ArrayList<>(nodes);
        ordered.sort(
            Comparator.<LayoutNode>comparingInt(node -> nodeHeight.get(node.id()))
                .thenComparing(node -> names.get(node.id()))
                .thenComparingInt(node -> color.get(node.id()))
                .thenComparing(
                    node -> node.id()
                        .toString()));
        return ordered;
    }

    /**
     * Canonical wiring color per node by iterated neighborhood refinement. Nodes start colored
     * by shape - size, port counts, height, name - then are repeatedly recolored by their own
     * color plus the sorted list of (own port, direction, peer color) link entries, until the
     * partition stops splitting. Two nodes share a final color only if no chain of links
     * distinguishes them, so an id tiebreak after this orders only interchangeable twins. Every
     * round before the fixpoint splits a color class, which bounds the work by
     * O(V * (V + E) * log V); real charts settle within a few rounds.
     */
    private static Map<UUID, Integer> wiringColors(final Collection<? extends LayoutNode> nodes,
        final Collection<Edge> links, final Map<UUID, String> names, final Map<UUID, Integer> nodeHeight) {
        record LinkEnd(String head, UUID peer) {}

        final Map<UUID, List<LinkEnd>> ends = new HashMap<>();
        for (final LayoutNode node : nodes) {
            ends.put(node.id(), new ArrayList<>());
        }
        for (final Edge link : links) {
            final List<LinkEnd> atSource = ends.get(link.sourceNodeId);
            final List<LinkEnd> atTarget = ends.get(link.targetNodeId);
            if (atSource == null || atTarget == null) continue;
            atSource
                .add(new LinkEnd("o" + link.sourceOutputIndex + ":" + link.targetInputIndex + ">", link.targetNodeId));
            atTarget
                .add(new LinkEnd("i" + link.targetInputIndex + ":" + link.sourceOutputIndex + "<", link.sourceNodeId));
        }

        Map<UUID, Integer> colors = null;
        int distinct = 0;
        while (true) {
            final Map<UUID, String> signatures = new HashMap<>();
            for (final LayoutNode node : nodes) {
                if (colors == null) {
                    signatures.put(
                        node.id(),
                        node.worldWidth() + "x"
                            + node.worldHeight()
                            + ":"
                            + node.inputCount()
                            + ":"
                            + node.outputCount()
                            + ":"
                            + nodeHeight.get(node.id())
                            + ":"
                            + names.get(node.id()));
                } else {
                    final List<String> entries = new ArrayList<>();
                    for (final LinkEnd end : ends.get(node.id())) {
                        entries.add(end.head() + colors.get(end.peer()));
                    }
                    Collections.sort(entries);
                    signatures.put(node.id(), colors.get(node.id()) + "|" + String.join(",", entries));
                }
            }

            // New colors are ranks in the sorted distinct signatures, so they depend only on
            // signature content, never on node iteration order.
            final List<String> sorted = new ArrayList<>(new TreeSet<>(signatures.values()));
            final Map<String, Integer> rankOf = new HashMap<>();
            for (int i = 0; i < sorted.size(); i++) {
                rankOf.put(sorted.get(i), i);
            }
            final Map<UUID, Integer> next = new HashMap<>();
            for (final Map.Entry<UUID, String> entry : signatures.entrySet()) {
                next.put(entry.getKey(), rankOf.get(entry.getValue()));
            }
            if (colors != null && sorted.size() == distinct) {
                return next;
            }
            distinct = sorted.size();
            colors = next;
        }
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
