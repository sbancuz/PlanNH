package com.sbancuz.plannh.gui.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.widget.sizer.Area;
import com.sbancuz.plannh.Config;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.client.Background;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.GraphData;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Note;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.HeaderTextWidget;
import com.sbancuz.plannh.gui.group.GroupWidget;
import com.sbancuz.plannh.gui.node.NodeWidget;
import com.sbancuz.plannh.gui.note.NoteWidget;

import it.unimi.dsi.fastutil.ints.IntIntPair;

/**
 * Handles the lifecycle of a layout request
 */
public final class ChartLayouter {

    /** Reserved in a group's frame, all round. The engine's layout is padded by this much. */
    private static final int GROUP_PAD = 12;

    private final CanvasWidget canvas;
    private final LayoutStrategy strategy;

    /** Immediate group of each machine and note, built by {@link #build()} and reused by apply. */
    private final Map<UUID, UUID> parentOf = new HashMap<>();

    /** A layout is in flight; client thread only. */
    private boolean layoutRunning = false;

    public ChartLayouter(final CanvasWidget canvas, final LayoutStrategy strategy) {
        this.canvas = canvas;
        this.strategy = strategy;
    }

    /** Entry point. The three phases below exist to be read, not to be called from elsewhere. */
    public void autoLayout() {
        if (layoutRunning) return;

        final LayoutRequest request = build();
        if (request.machines()
            .isEmpty()) return;

        final LayoutSettings settings = settings();
        layoutRunning = true;

        Background.offClient(() -> {
            try {
                return strategy.layout(request, settings);
            } catch (final RuntimeException | StackOverflowError | AssertionError e) {
                // A chart with stale positions beats a client that dies inside a click handler.
                PlanNH.LOG.error("Auto-layout failed; node positions left unchanged", e);
                return null;
            }
        }, plan -> {
            layoutRunning = false;
            if (plan != null && !plan.isEmpty()) apply(plan);
        });
    }

    /**
     * Reads the chart into a request, on the client thread.
     */
    private LayoutRequest build() {
        final List<LayoutMachine> machines = new ArrayList<>();
        final List<LayoutGroup> groups = new ArrayList<>();
        final List<Note> notes = new ArrayList<>();
        parentOf.clear();

        for (final Group group : canvas.getGraph()
            .getGroups()
            .values()) {
            groups.add(new LayoutGroup(group.getId(), new ArrayList<>(group.getChildren()
                .keySet()), GROUP_PAD, headerOf(group)));
            for (final GraphData child : group.getChildren()
                .values()) {
                if (child instanceof final Node node) {
                    addNode(machines, node, group.getId());
                } else if (child instanceof final Note note) {
                    notes.add(note);
                    parentOf.put(note.getId(), group.getId());
                }
            }
        }

        for (final Node node : canvas.getGraph()
            .getNodes()
            .values()) {
            addNode(machines, node, null);
        }
        notes.addAll(canvas.getGraph()
            .getNotes()
            .values());

        return new LayoutRequest(machines, relations(), groups, layoutNotes(notes, machines));
    }

    /**
     * Adds a machine to the request, measuring it from its widget.
     */
    private void addNode(final List<LayoutMachine> out, final Node node, final @Nullable UUID groupId ) {
        int width = 0;
        int height = 0;
        if (canvas.getNodeWidgets2()
            .get(node.getId()) instanceof final NodeWidget widget) {
            width = widget.getArea()
                .width;
            height = widget.getArea()
                .height;
        }

        out.add(new LayoutMachine(node.getId(), node.getMachineName(), width, height));
        if (groupId != null) parentOf.put(node.getId(), groupId);
    }

    private List<LayoutRelation> relations() {
        final List<LayoutRelation> relations = new ArrayList<>();
        for (final Edge edge : canvas.getGraph()
            .getEdges()
            .values()) {
            relations.add(new LayoutRelation(edge.getId(), edge.getSourceNodeId(), edge.getTargetNodeId()));
        }
        return relations;
    }

    /**
     * Measures every note, attaches it to a machine, and works out which face it belongs on.
     */
    private List<LayoutNote> layoutNotes(final List<Note> notes, final List<LayoutMachine> machines) {
        final Map<UUID, LayoutMachine> byId = new HashMap<>();
        for (final LayoutMachine machine : machines) byId.put(machine.id(), machine);

        final List<LayoutNote> out = new ArrayList<>(notes.size());
        for (final Note note : notes) {
            UUID anchor = nearestNode(note, byId);
            if (anchor == null) continue;

            final int[] size = noteSize(note);
            out.add(new LayoutNote(note.getId(), size[0], size[1], sideOf(note, anchor, byId), anchor));
        }
        return out;
    }

    private UUID nearestNode(final Note note, final Map<UUID, LayoutMachine> byId) {
        UUID best = null;
        long bestDistance = Long.MAX_VALUE;
        for (final LayoutMachine machine : byId.values()) {
            final GraphData at = dataOf(machine.id());
            if (at == null) continue;
            final long dx = at.getX() - note.getX();
            final long dy = at.getY() - note.getY();
            final long distance = dx * dx + dy * dy;
            if (distance < bestDistance || (distance == bestDistance && best != null
                && machine.id()
                    .compareTo(best) < 0)) {
                bestDistance = distance;
                best = machine.id();
            }
        }
        return best;
    }

    /** Whichever axis the note's centre is furthest from its anchor's on. {@code BELOW} on a tie. */
    private LayoutNote.Side sideOf(final Note note, final UUID anchor, final Map<UUID, LayoutMachine> byId) {
        final GraphData anchorData = dataOf(anchor);
        final LayoutMachine anchorMachine = byId.get(anchor);
        if (anchorData == null || anchorMachine == null) return LayoutNote.Side.BELOW;

        final int[] size = noteSize(note);
        final double dx = (note.getX() + size[0] / 2.0) - (anchorData.getX() + anchorMachine.width() / 2.0);
        final double dy = (note.getY() + size[1] / 2.0) - (anchorData.getY() + anchorMachine.height() / 2.0);
        if (Math.abs(dx) > Math.abs(dy)) return dx < 0 ? LayoutNote.Side.LEFT : LayoutNote.Side.RIGHT;
        return dy < 0 ? LayoutNote.Side.ABOVE : LayoutNote.Side.BELOW;
    }

/**
 * A note's measured box, or zero if its widget has not been measured.
 */
private int[] noteSize(final Note note) {
    if (canvas.getFlowchartWidgets()
        .get(note.getId()) instanceof final NoteWidget widget) {
        return new int[] { widget.getArea()
            .width, widget.getArea()
            .height };
    }
    return new int[] { 0, 0 };
}

    private void apply(final LayoutPlan plan) {
        final IntIntPair current = chartCorner();
        final IntIntPair planned = planCorner(plan);
        final int shiftX = current.leftInt() - planned.leftInt();
        final int shiftY = current.rightInt() - planned.rightInt();

        // One edit around the whole move. It sits outside the early-out above, so a failed layout leaves
        // neither the chart nor the undo history disturbed - an empty-result edit would leave a phantom
        // undo entry and make the button look like it did something.
        PlanAPI.recordEdit(canvas.getGraph(), () -> {
            writeGroups(plan, shiftX, shiftY);
            writeChildren(plan, shiftX, shiftY);
            repositionWidgets();
            // Not optional. Without it the arrows keep the geometry they had before the machines moved,
            // and nothing else invalidates them until some unrelated widget happens to move.
            canvas.needsReroute();
        });
    }

    private void writeGroups(final LayoutPlan plan, final int shiftX, final int shiftY) {
        for (final Map.Entry<UUID, Area> entry : plan.groupFrames()
            .entrySet()) {
            final Group group = canvas.getGraph()
                .getGroups()
                .get(entry.getKey());
            if (group == null) continue;

            final Area frame = entry.getValue();
            final int header = headerOf(group);
            group.setX(snap(frame.x + shiftX));
            group.setY(snap(frame.y + shiftY));
            group.setWidth(frame.width);
            group.setHeight(frame.height - header);
        }
    }

    private void writeChildren(final LayoutPlan plan, final int shiftX, final int shiftY) {
        for (final Map.Entry<UUID, IntIntPair> entry : plan.machines()
            .entrySet()) {
            final GraphData data = dataOf(entry.getKey());
            if (data == null) continue;
            final int[] at = toStored(entry.getValue(), entry.getKey(), shiftX, shiftY);
            data.setX(at[0]);
            data.setY(at[1]);
        }
        for (final Map.Entry<UUID, IntIntPair> entry : plan.notes()
            .entrySet()) {
            final GraphData data = dataOf(entry.getKey());
            if (data == null) continue;
            final int[] at = toStored(entry.getValue(), entry.getKey(), shiftX, shiftY);
            data.setX(at[0]);
            data.setY(at[1]);
        }
    }

    /**
     * A world position, snapped, converted into whatever the model stores for that thing.
     */
    private int[] toStored(final IntIntPair world, final UUID id, final int shiftX, final int shiftY) {
        final int x = snap(world.leftInt() + shiftX);
        final int y = snap(world.rightInt() + shiftY);

        final UUID groupId = parentOf.get(id);
        final Group group = groupId == null ? null
            : canvas.getGraph()
                .getGroups()
                .get(groupId);
        if (group == null) return new int[] { x, y };
        return new int[] { x - group.getX(), y - group.getY() - headerOf(group) };
    }

    /**
     * Puts every widget where the model now says it is.
     */
    private void repositionWidgets() {
        for (final FlowchartWidget<?, ?> widget : canvas.getFlowchartWidgets()
            .values()) {
            final GraphData data = widget.getData();
            widget.pos(data.getX(), data.getY());
            // A group's area measures its own children, and the children have just moved. Without the
            // resize the frame keeps the size it had, and the next layout reads that stale size instead of
            // the one just written.
            if (widget instanceof final GroupWidget<?> group) group.scheduleResize();
        }
    }

    private int snap(final int value) {
        if (!canvas.getGraph()
            .isSnapToGrid()) return value;
        return Math.round((float) value / CanvasWidget.GRID_SIZE) * CanvasWidget.GRID_SIZE;
    }

    // ===========================================================================================
    // The chart's corner
    // ===========================================================================================

    /** The chart's current top-left, over the same set the new corner is measured over. */
    private IntIntPair chartCorner() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (final Node node : canvas.getGraph()
            .getNodes()
            .values()) {
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
        }
        for (final Group group : canvas.getGraph()
            .getGroups()
            .values()) {
            minX = Math.min(minX, group.getX());
            minY = Math.min(minY, group.getY());
        }
        // Nothing loose and nothing framed: anchor on the origin rather than on MAX_VALUE.
        return minX == Integer.MAX_VALUE ? IntIntPair.of(0, 0) : IntIntPair.of(minX, minY);
    }

    /**
     * Where the plan puts the chart's corner, at offset zero.
     *
     * <p>
     * Loose machines and group frames only, for the reason {@link #chartCorner()} gives. Deriving this
     * from the engine's reported overall extent instead is specifically wrong: that covers every machine,
     * while a chart's corner belongs to loose machines and frames, and the two are never the same set once
     * anything is grouped - which is what made an earlier attempt walk the chart further off screen on
     * every press.
     */
    private IntIntPair planCorner(final LayoutPlan plan) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (final Map.Entry<UUID, IntIntPair> entry : plan.machines()
            .entrySet()) {
            if (parentOf.containsKey(entry.getKey())) continue;
            minX = Math.min(
                minX,
                entry.getValue()
                    .leftInt());
            minY = Math.min(
                minY,
                entry.getValue()
                    .rightInt());
        }
        for (final Area frame : plan.groupFrames()
            .values()) {
            minX = Math.min(minX, frame.x);
            minY = Math.min(minY, frame.y);
        }
        return minX == Integer.MAX_VALUE ? IntIntPair.of(0, 0) : IntIntPair.of(minX, minY);
    }

    // ===========================================================================================
    // Lookups
    // ===========================================================================================

    private LayoutSettings settings() {
        return Config.layoutSettings(CanvasWidget.requiredRouteCorridor(), Group.GROUP_MIN_W, Group.GROUP_MIN_H);
    }

    /** The model object behind an id, wherever it happens to live. */
    private GraphData dataOf(final UUID id) {
        final GraphData loose = canvas.getGraph()
            .getNodes()
            .get(id);
        if (loose != null) return loose;
        final GraphData looseNote = canvas.getGraph()
            .getNotes()
            .get(id);
        if (looseNote != null) return looseNote;
        for (final Group group : canvas.getGraph()
            .getGroups()
            .values()) {
            final GraphData child = group.getChildren()
                .get(id);
            if (child != null) return child;
        }
        return null;
    }

    /**
     * A group's title row, measured off its widget.
     *
     * <p>
     * <b>Measured, not declared.</b> The row is {@code coverChildrenHeight()} over the title text and the
     * button row, so its height is whatever the tallest child is - not the 20 that
     * {@code HeaderTextWidget} happens to declare. Falling back to that declared height when the widget
     * has not been laid out yet is better than nothing, because a group with no reserved header puts its
     * machines under the title.
     */
    private int headerOf(final Group group) {
        if (canvas.getFlowchartWidgets()
            .get(group.getId()) instanceof final GroupWidget<?> widget) {
            final int measured = widget.contentAreaOffset();
            if (measured > 0) return measured;
        }
        return HeaderTextWidget.HEIGHT;
    }
}
