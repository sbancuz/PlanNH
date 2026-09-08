package com.sbancuz.plannh.data.flowchart;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceView;
import com.sbancuz.plannh.data.flowchart.balancer.ChoiceKey;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Graph {

    /**
     * Sorted, so the graph hands its contents back in id order and every consumer that needs a
     * reproducible answer gets one without sorting first - the solver, the serializer, the router
     * and the layout all read these directly.
     */
    private final SortedMap<UUID, Node> nodes = new TreeMap<>();
    private final SortedMap<UUID, Edge> edges = new TreeMap<>();
    private final SortedMap<UUID, Edge2> edges2 = new TreeMap<>();
    private final SortedMap<UUID, Note> notes = new TreeMap<>();
    private final SortedMap<UUID, Group> groups = new TreeMap<>();

    @Setter
    private String name;

    @Setter
    private float zoom = 1f;
    @Setter
    private float panX;
    @Setter
    private float panY;
    @Setter
    private boolean snapToGrid;

    private BalanceMode balanceMode = BalanceMode.AUTO;

    /**
     * Per-graph undo/redo stack, transient because snapshots are content-encoded and never stored.
     */
    @Setter
    private transient UndoHistory undoHistory = new UndoHistory();

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

    /**
     * The graph version the solve caches above were built from.
     */
    private transient long solvedAt = -1;

    public Graph() {
        this.name = "";
    }

    public Graph(final String name) {
        this.name = name;
    }

    /**
     * Mutations bump the graph version internally; callers that change solve-relevant
     * state should route through the graph's own methods instead. This is closely related to the maps at
     * the beginning which should have proper accessors
     */
    public void bumpVersion() {
        version++;
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

    public void addNode(final Node node) {
        nodes.put(node.id, node);
        bumpVersion();
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
}
