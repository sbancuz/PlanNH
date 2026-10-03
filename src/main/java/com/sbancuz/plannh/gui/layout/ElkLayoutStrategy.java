package com.sbancuz.plannh.gui.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.elk.alg.layered.options.CrossingMinimizationStrategy;
import org.eclipse.elk.alg.layered.options.CycleBreakingStrategy;
import org.eclipse.elk.alg.layered.options.GraphCompactionStrategy;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.alg.layered.options.LayeringStrategy;
import org.eclipse.elk.alg.layered.options.NodePlacementStrategy;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.data.LayoutMetaDataService;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.options.HierarchyHandling;
import org.eclipse.elk.core.options.SizeOptions;
import org.eclipse.elk.core.util.NullElkProgressMonitor;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.util.ElkGraphUtil;

import lombok.SneakyThrows;

public final class ElkLayoutStrategy implements LayoutStrategy {

    private static final String ALGORITHM = "org.eclipse.elk.layered";

    private static final double EDGE_NODE_SPACING = 24.0;
    private static final double EDGE_CHANNEL_SPACING = 18.0;

    /** Clear space between a machine and the note anchored to it. */
    public static final int NOTE_GAP = 8;

    private static volatile boolean warmedUp;

    static {
        // Recursive layout clones property values as it descends into a hierarchy, and cloning an
        // internal value type like KVector goes through a registry that ELK's service loading does not
        // populate for us: it covers the algorithm providers, not this.
        LayoutMetaDataService.initElkReflect();
    }

    @Override
    public LayoutPlan layout(final LayoutRequest request, final LayoutSettings settings) {
        if (request.machines()
            .isEmpty()
            && request.groups()
                .isEmpty())
            return LayoutPlan.empty();

        final CanonicalOrder order = new CanonicalOrder(request);
        final Graph graph = build(order, request, settings);
        if (graph.machines.isEmpty()) return LayoutPlan.empty();

        run(graph.root, settings);
        // A group whose contents are smaller than the frame minimum has to be grown and laid out
        // again, because the extra room is only reserved if the engine knows about it before it places
        // the neighbours.
        if (growUndersizedFrames(graph, settings) > 0) run(graph.root, settings);

        return extract(graph, settings);
    }

    /** The ElkGraph plus the identity of everything in it, threaded through build, run and extract. */
    private static final class Graph {

        private final ElkNode root = ElkGraphUtil.createGraph();
        private final Map<UUID, ElkNode> machines = new LinkedHashMap<>();
        private final Map<UUID, ElkNode> groups = new LinkedHashMap<>();
        private final Map<UUID, LayoutGroup> groupSpecs = new LinkedHashMap<>();
        private final Map<UUID, List<LayoutNote>> notesByAnchor = new LinkedHashMap<>();

        /** Reverse lookups, so extraction is linear rather than a scan per node. */
        private final Map<ElkNode, UUID> idOfMachine = new IdentityHashMap<>();
        private final Map<ElkNode, UUID> idOfGroup = new IdentityHashMap<>();

        /** Which corner of its box a machine's widget sits in, once its notes have pushed it. */
        private final Map<UUID, int[]> machineInset = new HashMap<>();

        /** Where a note sits within its anchor's box. */
        private final Map<UUID, int[]> noteInset = new HashMap<>();

        /**
         * A group with a single member, as {@code group -> member}. Its frame is carried by the member's
         * box rather than by a compound, because a compound holding one node costs a hierarchical edge
         * and buys nothing.
         */
        private final Map<UUID, UUID> singleMemberGroup = new LinkedHashMap<>();
    }

    private Graph build(final CanonicalOrder order, final LayoutRequest request, final LayoutSettings settings) {
        final Graph graph = new Graph();
        applyOptions(
            graph.root,
            settings,
            GraphCompactionStrategy.LEFT_RIGHT_CONSTRAINT_LOCKING,
            NodePlacementStrategy.BRANDES_KOEPF);

        for (final LayoutGroup spec : request.groups()) graph.groupSpecs.put(spec.id(), spec);
        final Map<UUID, LayoutMachine> machineSpecs = new LinkedHashMap<>();
        for (final LayoutMachine machine : request.machines()) machineSpecs.put(machine.id(), machine);

        // Compounds first, outermost before innermost, so a nested one can attach to a parent that
        // already exists.
        //
        // A group only becomes a compound when there is something to compound. One machine in a box the
        // size of a machine gains nothing from being a hierarchy level and costs a great deal: see
        // shouldCompound.
        for (final UUID groupId : order.groupIds()) {
            final LayoutGroup spec = graph.groupSpecs.get(groupId);

            final UUID onlyMachine = soleMachineMember(spec, machineSpecs);
            if (onlyMachine != null) {
                graph.singleMemberGroup.put(groupId, onlyMachine);
                continue;
            }

            final ElkNode parent = graph.groups.get(order.parentOf(groupId));
            final ElkNode compound = ElkGraphUtil.createNode(parent == null ? graph.root : parent);
            compound.setProperty(CoreOptions.HIERARCHY_HANDLING, HierarchyHandling.SEPARATE_CHILDREN);
            compound.setProperty(CoreOptions.PADDING, paddingOf(spec));
            compound.setProperty(LayeredOptions.NODE_SIZE_OPTIONS, EnumSet.of(SizeOptions.DEFAULT_MINIMUM_SIZE));
            graph.groups.put(groupId, compound);
            graph.idOfGroup.put(compound, groupId);
            if (spec.memberIds()
                .isEmpty()) floorEmptyGroup(compound, spec, settings);
        }

        // Notes are grouped by anchor before anything is built, because a machine's box has to be grown
        // before it is created. A note whose anchor is absent from the chart reserves nothing and is
        // left where the author put it, which needs no special case: it simply never gets an inset.
        for (final LayoutNote note : request.notes()) {
            // A note with no anchor has nothing to reserve against, and one with no side has no direction
            // to reserve in. Either way it reserves nothing and the applier leaves it alone - which is
            // better than matching no side in the loop below and dropping the note without a word.
            if (note.anchorId() == null || note.side() == null) continue;
            if (!machineSpecs.containsKey(note.anchorId())) continue;
            graph.notesByAnchor.computeIfAbsent(note.anchorId(), key -> new ArrayList<>())
                .add(note);
        }
        for (final List<LayoutNote> anchored : graph.notesByAnchor.values()) {
            anchored.sort(Comparator.comparing(LayoutNote::id));
        }

        for (final UUID machineId : order.machines()) {
            final LayoutMachine spec = machineSpecs.get(machineId);
            final ElkNode parent = graph.groups.get(order.parentOf(machineId));
            final ElkNode node = ElkGraphUtil.createNode(parent == null ? graph.root : parent);
            final int[] box = reserveNoteSpace(spec, machineId, graph, settings);
            node.setDimensions(box[0], box[1]);
            graph.machines.put(machineId, node);
            graph.idOfMachine.put(node, machineId);
        }

        for (final LayoutRelation relation : order.relations()) {
            final ElkNode source = graph.machines.get(relation.sourceId());
            final ElkNode target = graph.machines.get(relation.targetId());
            // relations() has already dropped anything with a missing endpoint, and an edge with a
            // missing endpoint is a hard NPE inside ELK's importer rather than a graceful skip.
            if (source == null || target == null) continue;
            ElkGraphUtil.createSimpleEdge(source, target);
        }

        return graph;
    }

    /**
     * Grows a machine's box to hold the notes anchored to it, and records where everything lands
     * inside that box.
     */
    private int[] reserveNoteSpace(final LayoutMachine machine, final UUID machineId, final Graph graph,
        final LayoutSettings settings) {
        int width = machine.width();
        int height = machine.height();
        int insetLeft = 0;
        int insetTop = 0;

        // A group whose only member is this machine: its frame is reserved here rather than by a
        // compound. The frame is the whole grown box, so the minimum applies to the box, and the machine
        // sits one pad in from the left and one header-plus-pad down from the top - which is exactly
        // where the compound's padding would have put it.
        for (final Map.Entry<UUID, UUID> entry : graph.singleMemberGroup.entrySet()) {
            if (!entry.getValue()
                .equals(machineId)) continue;
            final LayoutGroup spec = graph.groupSpecs.get(entry.getKey());
            if (spec == null) continue;
            // The minimum has to be part of the box ELK places, not of the frame reported afterwards: a
            // frame reported wider than the reserved box is a frame a neighbour has already been placed
            // inside. Same reason as growUndersizedFrames, reached the same way.
            width = Math.max(width, settings.groupMinWidth() + 2 * spec.pad());
            height = Math.max(height, settings.groupMinHeight() + 2 * spec.pad() + spec.header());
            insetLeft = Math.max(insetLeft, spec.pad());
            insetTop = Math.max(insetTop, spec.header() + spec.pad());
        }

        final List<LayoutNote> anchored = graph.notesByAnchor.getOrDefault(machineId, List.of());
        for (final LayoutNote.Side side : LayoutNote.Side.values()) {
            final List<LayoutNote> onSide = anchored.stream()
                .filter(note -> note.side() == side)
                .toList();
            if (onSide.isEmpty()) continue;

            // The run is the notes plus one gap each, so the last note keeps a gap from the machine.
            int run = 0;
            for (final LayoutNote note : onSide) run += extent(note, side) + NOTE_GAP;
            if (side.vertical()) height += run;
            else width += run;

            // A note before the machine pushes the machine away from that edge; a note after it does
            // not, which is why only ABOVE and LEFT produce an inset.
            if (side == LayoutNote.Side.ABOVE) insetTop = run;
            if (side == LayoutNote.Side.LEFT) insetLeft = run;

            // Notes before the machine stack from the box's edge; notes after it start past the
            // machine, with a gap.
            // Offset from the machine's own position inside the box, not from the box origin: a machine
            // carrying a single-member group's frame does not start at the box's corner, and a note
            // stacked after it would otherwise land inside that frame.
            int cursor = switch (side) {
                case ABOVE, LEFT -> 0;
                case BELOW -> insetTop + machine.height() + NOTE_GAP;
                case RIGHT -> insetLeft + machine.width() + NOTE_GAP;
            };
            for (final LayoutNote note : onSide) {
                graph.noteInset.put(note.id(), side.vertical() ? new int[] { 0, cursor } : new int[] { cursor, 0 });
                cursor += extent(note, side) + NOTE_GAP;
            }
        }

        graph.machineInset.put(machineId, new int[] { insetLeft, insetTop });
        return new int[] { width, height };
    }

    private static int extent(final LayoutNote note, final LayoutNote.Side side) {
        return side.vertical() ? note.height() : note.width();
    }

    private static ElkPadding paddingOf(final LayoutGroup group) {
        // Asymmetric on purpose: the header sits above the contents, the pad on every other side.
        return new ElkPadding(group.header() + group.pad(), group.pad(), group.pad(), group.pad());
    }

    private static void applyOptions(final ElkNode root, final LayoutSettings settings,
        final GraphCompactionStrategy compaction, final NodePlacementStrategy placement) {

        root.setProperty(CoreOptions.ALGORITHM, ALGORITHM);
        root.setProperty(CoreOptions.DIRECTION, Direction.RIGHT);
        // Fixed rather than left to chance: the order handed to ELK is half the determinism promise and
        // this is the other half.
        root.setProperty(CoreOptions.RANDOM_SEED, 1);
        root.setProperty(CoreOptions.SPACING_NODE_NODE, (double) settings.nodeSpacing());
        root.setProperty(CoreOptions.SPACING_EDGE_NODE, EDGE_NODE_SPACING);
        root.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, (double) settings.layerSpacing());
        root.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, EDGE_CHANNEL_SPACING);
        root.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, EDGE_NODE_SPACING);
        root.setProperty(LayeredOptions.LAYERING_STRATEGY, LayeringStrategy.NETWORK_SIMPLEX);
        root.setProperty(
            LayeredOptions.CROSSING_MINIMIZATION_STRATEGY,
            CrossingMinimizationStrategy.MEDIAN_LAYER_SWEEP);
        root.setProperty(LayeredOptions.CYCLE_BREAKING_STRATEGY, CycleBreakingStrategy.DEPTH_FIRST);
        root.setProperty(LayeredOptions.NODE_PLACEMENT_STRATEGY, placement);
        root.setProperty(LayeredOptions.THOROUGHNESS, settings.thoroughness());
        root.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);
        root.setProperty(LayeredOptions.COMPACTION_POST_COMPACTION_STRATEGY, compaction);
    }

    /**
     * The order configurations are attempted in, best first.
     */
    private static final Object[][] ATTEMPTS = {
        { GraphCompactionStrategy.LEFT_RIGHT_CONSTRAINT_LOCKING, NodePlacementStrategy.BRANDES_KOEPF },
        { GraphCompactionStrategy.NONE, NodePlacementStrategy.BRANDES_KOEPF },
        { GraphCompactionStrategy.LEFT_RIGHT_CONSTRAINT_LOCKING, NodePlacementStrategy.SIMPLE },
        { GraphCompactionStrategy.NONE, NodePlacementStrategy.SIMPLE }, };

    /**
     * Runs the engine, backing off through {@link #ATTEMPTS} until one succeeds.
     */
    @SneakyThrows
    private static void run(final ElkNode root, final LayoutSettings settings) {
        Throwable first = null;
        for (final Object[] attempt : ATTEMPTS) {
            try {
                applyOptions(root, settings, (GraphCompactionStrategy) attempt[0], (NodePlacementStrategy) attempt[1]);
                new RecursiveGraphLayoutEngine().layout(root, new NullElkProgressMonitor());
                return;
            } catch (final RuntimeException | StackOverflowError | AssertionError e) {
                if (first == null) first = e;
            }
        }
        throw first;
    }

    /**
     * The single machine a group's frame can ride on instead of becoming a compound, or null.
     */
    private static UUID soleMachineMember(final LayoutGroup group, final Map<UUID, LayoutMachine> machines) {
        UUID sole = null;
        for (final UUID member : group.memberIds()) {
            if (machines.containsKey(member)) {
                if (sole != null) return null;
                sole = member;
            } else {
                // A nested group, or stale membership: either way this is not a one-machine group.
                return null;
            }
        }
        return sole;
    }

    /**
     * Gives an empty group a child sized so that the compound comes out at exactly its frame minimum.
     */
    private void floorEmptyGroup(final ElkNode compound, final LayoutGroup spec, final LayoutSettings settings) {
        final ElkNode filler = ElkGraphUtil.createNode(compound);
        // Undo the padding: the compound is the filler plus the pad on every side, and the header counts
        // as top padding.
        filler.setDimensions(settings.groupMinWidth(), settings.groupMinHeight() + spec.header());
    }

    /**
     * Grows any compound that came out smaller than its frame minimum, by widening its padding.
     *
     * @return how many compounds were grown, so the caller knows whether a re-run is worth it
     */
    private int growUndersizedFrames(final Graph graph, final LayoutSettings settings) {
        int grown = 0;
        for (final Map.Entry<UUID, ElkNode> entry : graph.groups.entrySet()) {
            final LayoutGroup spec = graph.groupSpecs.get(entry.getKey());
            if (spec == null) continue;
            final ElkNode compound = entry.getValue();
            final ElkPadding padding = compound.getProperty(CoreOptions.PADDING);

            final int shortByX = settings.groupMinWidth() + 2 * spec.pad() - (int) Math.round(compound.getWidth());
            final int shortByY = settings.groupMinHeight() + 2 * spec.pad()
                + spec.header()
                - (int) Math.round(compound.getHeight());
            if (shortByX <= 0 && shortByY <= 0) continue;

            compound.setProperty(
                CoreOptions.PADDING,
                new ElkPadding(
                    padding.getTop(),
                    padding.getRight() + Math.max(0, shortByX),
                    padding.getBottom() + Math.max(0, shortByY),
                    padding.getLeft()));
            grown++;
        }
        return grown;
    }

    /**
     * Turns ELK's coordinates into world coordinates.
     */
    private LayoutPlan extract(final Graph graph, final LayoutSettings settings) {
        final Map<UUID, Point> machines = new LinkedHashMap<>();
        final Map<UUID, Box> frames = new LinkedHashMap<>();
        final Map<UUID, Point> notes = new LinkedHashMap<>();

        // The root sits at the origin with no padding, so its children are already world.
        descend(graph.root, 0, 0, graph, settings, machines, frames, notes);

        if (machines.isEmpty()) return LayoutPlan.empty();
        return new LayoutPlan(machines, frames, notes);
    }

    /**
     * @param parentX world x of {@code parent}'s own origin, which is also its frame's top-left
     */
    private void descend(final ElkNode parent, final int parentX, final int parentY, final Graph graph,
        final LayoutSettings settings, final Map<UUID, Point> machines, final Map<UUID, Box> frames,
        final Map<UUID, Point> notes) {

        for (final ElkNode child : parent.getChildren()) {
            // A plain addition. The padding between the parent's origin and its contents is already
            // folded into the child's own coordinates; adding it here as well is what pushes content
            // two groups deep out of its frame by exactly the pad, and only there.
            final int boxX = parentX + (int) Math.round(child.getX());
            final int boxY = parentY + (int) Math.round(child.getY());

            final UUID groupId = graph.idOfGroup.get(child);
            if (groupId != null) {
                frames.put(groupId, frameOf(child, boxX, boxY, graph, settings));
                descend(child, boxX, boxY, graph, settings, machines, frames, notes);
                continue;
            }

            final UUID machineId = graph.idOfMachine.get(child);
            if (machineId == null) continue;
            final int[] inset = graph.machineInset.get(machineId);
            machines.put(machineId, new Point(boxX + inset[0], boxY + inset[1]));

            // A single-member group's frame is its member's grown box, so it is reported from here rather
            // than from a compound that does not exist.
            for (final Map.Entry<UUID, UUID> entry : graph.singleMemberGroup.entrySet()) {
                if (!entry.getValue()
                    .equals(machineId)) continue;
                final LayoutGroup spec = graph.groupSpecs.get(entry.getKey());
                frames.put(
                    entry.getKey(),
                    new Box(
                        boxX,
                        boxY,
                        Math.max((int) Math.round(child.getWidth()), settings.groupMinWidth() + 2 * spec.pad()),
                        Math.max(
                            (int) Math.round(child.getHeight()),
                            settings.groupMinHeight() + 2 * spec.pad() + spec.header())));
            }
            for (final LayoutNote note : graph.notesByAnchor.getOrDefault(machineId, List.of())) {
                final int[] noteInset = graph.noteInset.get(note.id());
                if (noteInset != null) notes.put(note.id(), new Point(boxX + noteInset[0], boxY + noteInset[1]));
            }
        }
    }

    private Box frameOf(final ElkNode compound, final int boxX, final int boxY, final Graph graph,
        final LayoutSettings settings) {

        final LayoutGroup spec = graph.groupSpecs.get(graph.idOfGroup.get(compound));
        final int pad = spec == null ? 0 : spec.pad();
        final int header = spec == null ? 0 : spec.header();

        final int width = Math.max((int) Math.round(compound.getWidth()), settings.groupMinWidth() + 2 * pad);
        final int height = Math
            .max((int) Math.round(compound.getHeight()), settings.groupMinHeight() + 2 * pad + header);
        return new Box(boxX, boxY, width, height);
    }

    public static void warmUp() {
        if (warmedUp) return;
        try {
            new ElkLayoutStrategy().layout(warmupRequest(), LayoutSettings.DEFAULT);
            warmedUp = true;
        } catch (final RuntimeException | StackOverflowError | AssertionError ignored) {
            // The first real layout would surface it anyway; failing here would only move the cost.
        }
    }

    private static LayoutRequest warmupRequest() {
        final UUID a = UUID.nameUUIDFromBytes("plannh-warmup-a".getBytes());
        final UUID b = UUID.nameUUIDFromBytes("plannh-warmup-b".getBytes());
        return new LayoutRequest(
            List.of(new LayoutMachine(a, "a", 100, 80), new LayoutMachine(b, "b", 100, 80)),
            List.of(new LayoutRelation(UUID.nameUUIDFromBytes("plannh-warmup-edge".getBytes()), a, b)),
            List.of(),
            List.of());
    }
}
