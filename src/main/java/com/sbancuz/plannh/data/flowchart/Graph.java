package com.sbancuz.plannh.data.flowchart;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.data.flowchart.Summary.SummarySection;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceView;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.data.flowchart.balancer.ChoiceKey;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Alternatives;

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
    @Getter
    private boolean opsMode;

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
     * Keyed rather than one field per knob, because which knobs a chart has a floor for is the
     * installed mods' business, not this package's - naming them here would put GregTech in a class
     * that has to stay loadable without it. Sorted so a save writes them in a stable order.
     */
    private final SortedMap<String, Integer> minimums = new TreeMap<>();

    /**
     * Per-graph undo/redo stack, transient because snapshots are content-encoded and never stored.
     */
    public final transient UndoHistory undoHistory = new UndoHistory();

    /**
     * Which summary sections the user has folded away in this graph's panel.
     */
    public transient EnumSet<SummarySection> collapsedSummarySections = defaultSummaryFolds();

    /**
     * Which of the equally-workable answers the user picked, or null for the solver's own. Applied
     * as a preference, never as a constraint: a key that no longer fits the chart is dropped with a
     * note rather than allowed to degrade it.
     */
    @Getter
    private ChoiceKey excessChoice;

    private BalanceResult balance = null;
    private Summary summary = null;
    /**
     * The two display views, built on first ask after a solve rather than with it: the canvas wants
     * the boundary every frame and never the choices, the summary panel wants the reverse.
     */
    private List<BalanceView.Boundary> boundaryView = null;
    private BalanceView.Choices choicesView = null;

    private boolean dirty = true;

    public Graph() {
        this.name = "";
    }

    public Graph(final String name) {
        this.name = name;
    }

    public static EnumSet<SummarySection> defaultSummaryFolds() {
        return EnumSet.of(SummarySection.MACHINE_COUNTS, SummarySection.STATISTICS, SummarySection.HELP);
    }

    public void markDirty() {
        dirty = true;
    }

    public void setExcessChoice(final ChoiceKey choice) {
        excessChoice = choice;
        markDirty();
    }

    public void setBalanceMode(final BalanceMode mode) {
        balanceMode = mode;
        markDirty();
    }

    public void setOpsMode(final boolean opsMode) {
        this.opsMode = opsMode;
        markDirty();
    }

    /** What this chart plans at for one knob, or {@link #NO_MINIMUM} when it has not said. */
    public int getMinimum(final String settingKey) {
        return minimums.getOrDefault(settingKey, NO_MINIMUM);
    }

    /**
     * A minimum changes what an untouched node runs at, which changes its parallel count and so the
     * whole solve. Hence markDirty rather than a plain setter.
     */
    public void setMinimum(final String settingKey, final int tier) {
        minimums.put(settingKey, tier);
        markDirty();
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
        markDirty();
    }

    public BalanceResult balance() {
        if (dirty) {
            balance = Balancer.balance(this, balanceMode, opsMode);
            summary = Summary.compute(balance, this, opsMode);
            boundaryView = null;
            choicesView = null;
            dirty = false;
        }
        return balance;
    }

    /**
     * Every answer that balances this chart as well as the one on screen. Produced by the same pass
     * that produced the balance, so asking costs nothing beyond the solve that already happened.
     */
    public Alternatives alternatives() {
        final BalanceResult balance = balance();
        return balance instanceof final BalanceResult.Solved solved ? solved.alternatives()
            : new Alternatives(null, List.of(), true, List.of());
    }

    public Summary summary() {
        balance(); // ensure up-to-date
        return summary;
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

    /** The equally-workable answers, grouped by the question each one answers. Cached as above. */
    public BalanceView.Choices choices() {
        balance();
        if (choicesView == null) choicesView = BalanceView.choices(this);
        return choicesView;
    }

    public Collection<Node> getNodes() {
        return nodes.values();
    }

    public void addNode(final Node node) {
        nodes.put(node.id, node);
        markDirty();
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
        markDirty();
    }

    public void removeEdge(final UUID id) {
        edges.remove(id);
        markDirty();
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
