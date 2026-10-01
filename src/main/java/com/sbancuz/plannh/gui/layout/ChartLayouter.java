package com.sbancuz.plannh.gui.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.sbancuz.plannh.Config;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Edge2;
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

/**
 * Lays the chart out: build a request, hand it to the engine, write the answer back.
 *
 * <p>
 * Owns the three phases and every piece of arithmetic that turns world coordinates into the numbers the
 * model stores. The split is the point — phase two is pure and touches no widget, which is what would let
 * it move to a worker without changing anything else here.
 *
 * <p>
 * <b>Why this is an object and not three static helpers.</b> It holds the canvas and the strategy, it is
 * constructed once with both, and nothing reaches past it to either. The engine is a constructor
 * parameter threaded from {@code FlowchartScreen}, so nothing in {@code CanvasWidget} ever names a
 * concrete implementation. Declaring the type and then hard-wiring the value is the thing an earlier
 * design got wrong, and it looks identical from the outside.
 *
 * <p>
 * <b>One enumeration of the chart's contents</b>, in {@link #build()}, and nothing else walks the model.
 * A machine or note filed inside a group is <em>not</em> in {@code graph.getNodes()} /
 * {@code graph.getNotes()}; it lives in its group's children. Reading either map finds the loose ones and
 * quietly loses every grouped one — half the chart, no error — and that has been got wrong in four places
 * in this codebase's history.
 */
public final class ChartLayouter {

    /** Reserved in a group's frame, all round. The engine's layout is padded by this much. */
    private static final int GROUP_PAD = 12;

    private final CanvasWidget canvas;
    private final LayoutStrategy strategy;

    /** Immediate group of each machine and note, built by {@link #build()} and reused by apply. */
    private final Map<UUID, UUID> parentOf = new HashMap<>();

    public ChartLayouter(final CanvasWidget canvas, final LayoutStrategy strategy) {
        this.canvas = canvas;
        this.strategy = strategy;
    }

    /** Entry point. The three phases below exist to be read, not to be called from elsewhere. */
    public void autoLayout() {
        final LayoutRequest request = build();
        if (request.machines()
            .isEmpty()) return;

        final LayoutPlan plan;
        try {
            plan = strategy.layout(request, settings());
        } catch (final RuntimeException | StackOverflowError | AssertionError e) {
            // A chart with stale positions beats a client that dies inside a click handler.
            PlanNH.LOG.error("Auto-layout failed; node positions left unchanged", e);
            return;
        }
        if (plan.isEmpty()) return;

        apply(plan);
    }

    // ===========================================================================================
    // Phase 1 - build
    // ===========================================================================================

    /**
     * Reads the chart into a request, on the client thread.
     *
     * <p>
     * The model says <em>what</em> to lay out and the widgets are only asked <em>how big</em> things are.
     * Keeping those apart is not tidiness: the widget map is a strict superset of the model, because
     * {@code NodeWidget} registers itself whether or not it is filed inside a group, so iterating widgets
     * would quietly disagree with the chart about what exists.
     */
    private LayoutRequest build() {
        final List<LayoutMachine> machines = new ArrayList<>();
        final List<LayoutGroup> groups = new ArrayList<>();
        final List<Note> notes = new ArrayList<>();
        parentOf.clear();

        final int[] unmeasured = { 0 };

        for (final Group group : canvas.getGraph()
            .getGroups()
            .values()) {
            groups.add(new LayoutGroup(group.getId(), new ArrayList<>(group.getChildren()
                .keySet()), GROUP_PAD, headerOf(group)));
            for (final GraphData child : group.getChildren()
                .values()) {
                if (child instanceof final Node node) {
                    addMachine(machines, node, group.getId(), unmeasured);
                } else if (child instanceof final Note note) {
                    notes.add(note);
                    parentOf.put(note.getId(), group.getId());
                }
            }
        }
        for (final Node node : canvas.getGraph()
            .getNodes()
            .values()) {
            addMachine(machines, node, null, unmeasured);
        }
        notes.addAll(canvas.getGraph()
            .getNotes()
            .values());

        if (unmeasured[0] > 0) {
            PlanNH.LOG.debug(
                "Auto-layout: {} machine(s) reported a zero box and were laid out as dots",
                unmeasured[0]);
        }

        return new LayoutRequest(machines, relations(), groups, layoutNotes(notes, machines));
    }

    /**
     * Adds a machine to the request, measuring it from its widget.
     *
     * <p>
     * <b>No guessed size.</b> The widget is the only thing that knows how big a machine is — nothing in
     * {@code data.flowchart} stores it — so whatever it reports is what the engine gets, including a
     * zero. A machine measured at zero is laid out as a dot in the right place, which is wrong but
     * visible; a machine measured at an invented 120x80 is laid out overlapping whatever was really
     * there, which is wrong and not visible.
     *
     * @param unmeasured incremented, not used for control flow — it exists so the count shows up in a
     *                   log line rather than being a silent zero
     */
    private void addMachine(final List<LayoutMachine> out, final Node node, final @Nullable UUID groupId,
        final int[] unmeasured) {

        int width = 0;
        int height = 0;
        if (canvas.getNodeWidgets2()
            .get(node.getId()) instanceof final NodeWidget widget) {
            width = widget.getArea()
                .width;
            height = widget.getArea()
                .height;
        }
        if (width <= 0 || height <= 0) unmeasured[0]++;
        out.add(new LayoutMachine(node.getId(), node.getMachineName(), width, height));
        if (groupId != null) parentOf.put(node.getId(), groupId);
    }

    /**
     * The live relations.
     *
     * <p>
     * {@code getEdges2}, not {@code getEdges}. The latter is the legacy type the old engine wanted, it has
     * no producer anywhere in {@code src/main}, and the serializer reads and writes the {@code Edge2} map
     * — so a reloaded chart has an empty one and the button does nothing at all, which is precisely what
     * used to happen.
     */
    private List<LayoutRelation> relations() {
        final List<LayoutRelation> relations = new ArrayList<>();
        for (final Edge2 edge : canvas.getGraph()
            .getEdges2()
            .values()) {
            relations.add(new LayoutRelation(edge.getId(), edge.getSourceNodeId(), edge.getTargetNodeId()));
        }
        return relations;
    }

    /**
     * Measures every note, attaches it to a machine, and works out which face it belongs on.
     *
     * <p>
     * <b>The anchor is persisted and resolved once.</b> A note is placed rigidly relative to its anchor,
     * so after one layout the machine nearest the note's new position is usually the same one — but in a
     * dense column a short machine above can be nearer than a tall anchor below, and recomputing from a
     * position the previous run just produced makes the chart drift a little further on every press.
     * See {@link Note#anchorId}.
     *
     * <p>
     * <b>The side is re-derived every time</b>, because dragging a note to the other side of its machine is
     * a legitimate thing to want and it costs one comparison.
     *
     * <p>
     * A note with no machine on the chart is left out of the request entirely, which is the whole of its
     * behaviour in that case: the applier only writes coordinates for notes the plan mentions, so it stays
     * where the author put it.
     */
    private List<LayoutNote> layoutNotes(final List<Note> notes, final List<LayoutMachine> machines) {
        final Map<UUID, LayoutMachine> byId = new HashMap<>();
        for (final LayoutMachine machine : machines) byId.put(machine.id(), machine);

        final List<LayoutNote> out = new ArrayList<>(notes.size());
        for (final Note note : notes) {
            UUID anchor = note.getAnchorId();
            if (anchor == null || !byId.containsKey(anchor)) {
                anchor = nearestMachine(note, byId);
                if (anchor != null) note.setAnchorId(anchor);
            }
            // No machine on the chart: the note is left out of the request, and the applier only writes
            // coordinates for notes the plan mentions, so it stays exactly where the author put it.
            if (anchor == null) continue;

            final int[] size = noteSize(note);
            out.add(new LayoutNote(note.getId(), size[0], size[1], sideOf(note, anchor, byId), anchor));
        }
        return out;
    }

    /**
     * The machine a note is nearest, measured in the coordinates both of them are stored in.
     *
     * <p>
     * <b>Stored, not world.</b> A note inside a group stores a group-relative coordinate and a loose one
     * stores a world coordinate, so across a mixed chart this compares two different spaces. That is
     * acceptable because it only has to pick something deterministic and plausible: the engine then
     * reserves real space on that machine's box and places both consistently, so the guess cannot
     * accumulate. Lifting everything to world space would mean threading the header and the parent chain
     * through the builder for one tiebreak.
     *
     * <p>
     * Ties break on id, so the choice cannot depend on iteration order — which is the same requirement
     * the engine's own ordering exists to meet.
     */
    private UUID nearestMachine(final Note note, final Map<UUID, LayoutMachine> byId) {
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
 *
 * <p>
 * Same rule as {@link #addMachine}: the widget is the only source, and nothing is guessed. A
 * {@code NoteWidget} is pure {@code coverChildren()}, so unlike a machine it has no declared size to
 * fall back to even in principle.
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

    // ===========================================================================================
    // Phase 3 - apply
    // ===========================================================================================

    /**
     * Writes a plan onto the chart.
     *
     * <p>
     * <b>Anchored in two passes.</b> The plan is laid out at offset zero, the chart's corner is measured
     * before and after, and everything is shifted by the difference. Because a translation is affine, that
     * lands on the anchor exactly, with no rounding in the argument.
     *
     * <p>
     * The two corners read different things — the current one from the model, the new one from the plan —
     * so they cannot be one method. What has to match is the <em>predicate</em>: both take the minimum over
     * loose machines and group frames, and neither counts anything nested inside a group, because a nested
     * stored position is not a world coordinate at all. An earlier attempt implemented that predicate three
     * times in three files and drifted.
     */
    private void apply(final LayoutPlan plan) {
        final Point current = chartCorner();
        final Point planned = planCorner(plan);
        final int shiftX = current.x() - planned.x();
        final int shiftY = current.y() - planned.y();

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

    /**
     * A group's stored position and size, from the frame the engine reserved.
     *
     * <p>
     * {@code Group.width} and {@code Group.height} are the <em>area</em> size, not the frame's: the frame
     * is the area plus the title row above it. So the width is the frame's width unchanged, and the height
     * is the frame's height less the header.
     */
    private void writeGroups(final LayoutPlan plan, final int shiftX, final int shiftY) {
        for (final Map.Entry<UUID, Box> entry : plan.groupFrames()
            .entrySet()) {
            final Group group = canvas.getGraph()
                .getGroups()
                .get(entry.getKey());
            if (group == null) continue;

            final Box frame = entry.getValue();
            final int header = headerOf(group);
            group.setX(snap(frame.x() + shiftX));
            group.setY(snap(frame.y() + shiftY));
            group.setWidth(frame.width());
            group.setHeight(frame.height() - header);
        }
    }

    /**
     * Every machine and note, from world coordinates to the coordinate the model stores.
     *
     * <p>
     * <b>Snapping happens in world space, before the conversion.</b> Snapping a group and its members
     * independently would move each by up to a grid step relative to the other and break both the frame
     * and the clearance the engine promised. A grouped member's stored x is then not itself grid-aligned,
     * which is fine: the grid is a world-space drawing aid, and the thing that has to stay correct is the
     * gap between machines.
     */
    private void writeChildren(final LayoutPlan plan, final int shiftX, final int shiftY) {
        for (final Map.Entry<UUID, Point> entry : plan.machines()
            .entrySet()) {
            final GraphData data = dataOf(entry.getKey());
            if (data == null) continue;
            final int[] at = toStored(entry.getValue(), entry.getKey(), shiftX, shiftY);
            data.setX(at[0]);
            data.setY(at[1]);
        }
        for (final Map.Entry<UUID, Point> entry : plan.notes()
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
     *
     * <p>
     * Loose things store world coordinates. Things inside a group store coordinates relative to that
     * group's content area, so exactly one subtraction — and only one, because a stored coordinate is always
     * relative to its <em>immediate</em> parent, whatever depth that parent sits at.
     *
     * <p>
     * The header is subtracted here because it is the same measured value the engine reserved it against,
     * read out of the same widget. Subtracting it in both places is the bug that put every grouped machine
     * one header above where the engine put it, and reading it twice is what made that happen.
     */
    private int[] toStored(final Point world, final UUID id, final int shiftX, final int shiftY) {
        final int x = snap(world.x() + shiftX);
        final int y = snap(world.y() + shiftY);

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
     *
     * <p>
     * {@code pos()} takes a parent-relative coordinate, which is exactly what the model stores for a
     * grouped child and exactly what it stores for a loose one. Groups additionally need a resize, because
     * the area measures its own children and the children just moved - without it the frame keeps the size
     * it had, and the next layout reads that stale size instead of the one it just wrote.
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
    private Point chartCorner() {
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
        return minX == Integer.MAX_VALUE ? new Point(0, 0) : new Point(minX, minY);
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
    private Point planCorner(final LayoutPlan plan) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (final Map.Entry<UUID, Point> entry : plan.machines()
            .entrySet()) {
            if (parentOf.containsKey(entry.getKey())) continue;
            minX = Math.min(
                minX,
                entry.getValue()
                    .x());
            minY = Math.min(
                minY,
                entry.getValue()
                    .y());
        }
        for (final Box frame : plan.groupFrames()
            .values()) {
            minX = Math.min(minX, frame.x());
            minY = Math.min(minY, frame.y());
        }
        return minX == Integer.MAX_VALUE ? new Point(0, 0) : new Point(minX, minY);
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
