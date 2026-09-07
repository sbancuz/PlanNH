package com.sbancuz.plannh.data.flowchart;

import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceView;
import com.sbancuz.plannh.data.flowchart.balancer.ChoiceKey;

import lombok.Getter;
import lombok.Setter;

public class Graph {

    // TODO make these use getters
    /**
     * Sorted, so the graph hands its contents back in id order and every consumer that needs a
     * reproducible answer gets one without sorting first - the solver, the serializer, the router
     * and the layout all read these directly.
     */
    public final SortedMap<UUID, Node> nodes = new TreeMap<>();
    public final SortedMap<UUID, Edge> edges = new TreeMap<>();
    public final SortedMap<UUID, Note> notes = new TreeMap<>();
    public final SortedMap<UUID, Group> groups = new TreeMap<>();

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private float zoom = 1f;
    @Getter
    @Setter
    private float panX;
    @Getter
    @Setter
    private float panY;
    @Getter
    @Setter
    private boolean snapToGrid;

    @Getter
    private BalanceMode balanceMode = BalanceMode.AUTO;

    /** Nothing is set for this key, so a node opens on the best the game offers. */
    public static final int NO_MINIMUM = -1;

    /**
     * The structure this chart assumes it can build, keyed by setting. A chart describes a factory at
     * one point in a world's progression, so the coil a node opens on belongs to the chart rather than
     * to each node; setting it once is what keeps a node's own settings down to what makes that node
     * different.
     *
     * <p>
     * A starting value, not a ceiling. A recipe that needs more raises its own node, and a row the
     * user edits keeps what it was given.
     *
     * <p>
     * Keyed rather than one field per setting, because which settings a chart has a floor for is the
     * installed mods' business, not this package's - naming them here would put GregTech in a class
     * that has to stay loadable without it. Sorted so a save writes them in a stable order.
     */
    private final SortedMap<String, Integer> minimums = new TreeMap<>();

    /**
     * Per-graph undo/redo stack, transient because snapshots are content-encoded and never stored.
     */
    public final transient UndoHistory undoHistory = new UndoHistory();

    /**
     * The display view, built on first ask after a solve rather than with it: the canvas wants the
     * boundary every frame and never the choices, which the summary reads straight from the solve.
     */
    private List<BalanceView.Boundary> boundaryView = null;

    /**
     * Monotonic counter bumped on every mutation; derived caches (the solve, the summary, the
     * boundary view) each compare against it to know when they are stale. Transient because
     * a loaded plan starts cold and re-derives everything on first ask.
     */
    private transient long version = 0;

    /** The graph version the solve caches above were built from. */
    private transient long solvedAt = -1;

    public Graph() {
        this.name = "";
    }

    public Graph(final String name) {
        this.name = name;
    }

    public long version() {
        return version;
    }

    private void bumpVersion() {
        version++;
    }

    /**
     * @deprecated Mutations bump the graph version internally; callers that change solve-relevant
     *             state should route through the graph's own methods instead. This is closely related to the maps at
     *             the beginning which should have proper accessors
     */
    @Deprecated
    public void markDirty() {
        bumpVersion();
    }

    public ChoiceKey getExcessChoice() {
        return Plan.getInstance()
            .getSummary()
            .getExcessChoice();
    }

    public void setExcessChoice(final ChoiceKey choice) {
        Plan.getInstance()
            .getSummary()
            .setExcessChoice(choice);
        bumpVersion();
    }

    public void setBalanceMode(final BalanceMode mode) {
        balanceMode = mode;
        bumpVersion();
    }

    /** What this chart plans at for one setting, or {@link #NO_MINIMUM} when it has not said. */
    public int getMinimum(final String settingKey) {
        return minimums.getOrDefault(settingKey, NO_MINIMUM);
    }

    /**
     * A minimum changes what an untouched node runs at, which changes its parallel count and so the
     * whole solve.
     */
    public void setMinimum(final String settingKey, final int tier) {
        minimums.put(settingKey, tier);
        bumpVersion();
    }

    /** The stored floors, for the serializer. Sorted, so a save is reproducible. */
    @Nonnull
    public SortedMap<String, Integer> getMinimums() {
        return minimums;
    }

    public void removeNode(final UUID id) {
        nodes.remove(id);
        edges.values()
            .removeIf(e -> e.sourceNodeId.equals(id) || e.targetNodeId.equals(id));
        bumpVersion();
    }

    public BalanceResult balance() {
        if (solvedAt != version) {
            Plan.getInstance()
                .getSummary()
                .recompute(this);
            solvedAt = version;
        }
        return Plan.getInstance()
            .getSummary()
            .balance();
    }

    /**
     * Everything crossing the chart's boundary. Held from solve to solve because the canvas asks
     * once per frame and the answer only moves when the chart does.
     */
    public List<BalanceView.Boundary> boundary() {
        balance(); // drops a view built before the last edit
        if (boundaryView == null) boundaryView = BalanceView.boundary(this);
        return boundaryView;
    }

    public Collection<Node> getNodes() {
        return nodes.values();
    }

    public void addNode(final Node node) {
        nodes.put(node.id, node);
        bumpVersion();
    }

    public Collection<Edge> getEdges() {
        return edges.values();
    }

    public void addEdge(final Edge edge) {
        // A source-port/target-port pair carries at most one edge: re-wiring it replaces the
        // existing edge instead of stacking a duplicate.
        edges.values()
            .removeIf(
                e -> e.sourceNodeId.equals(edge.sourceNodeId) && e.sourceOutputIndex == edge.sourceOutputIndex
                    && e.targetNodeId.equals(edge.targetNodeId)
                    && e.targetInputIndex == edge.targetInputIndex);
        edges.put(edge.id, edge);
        bumpVersion();
    }

    public void removeEdge(final UUID id) {
        edges.remove(id);
        bumpVersion();
    }

    /**
     * First input port of {@code dst} that accepts {@code src}'s given output; -1 when none is
     * compatible.
     */
    public int findCompatibleInput(final Node src, final int srcOutIdx, final Node dst) {
        if (src == dst || srcOutIdx < 0 || srcOutIdx >= src.outputs.size()) return -1;
        final Port<?> out = src.outputs.get(srcOutIdx);
        for (int i = 0; i < dst.inputs.size(); i++) {
            if (out.canConnect(dst.inputs.get(i))) return i;
        }
        return -1;
    }

    public void removeGroup(final UUID id) {
        groups.remove(id);
    }

    public Collection<Group> getGroups() {
        return groups.values();
    }

    public Collection<Note> getNotes() {
        return notes.values();
    }
}
