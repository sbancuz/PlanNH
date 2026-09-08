package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Alternative;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Alternatives;
import com.sbancuz.plannh.gui.GuiHelper;

/**
 * What the balancer's answer looks like to a reader, as data rather than as pixels. Everything the
 * canvas and the summary panel say about the chart's boundary is decided here and merely drawn
 * there, which leaves the widgets nothing to do but position and colour pre-built rows.
 *
 * <p>
 * Minecraft-free, so it runs under a plain JUnit. Rates go through {@link GuiHelper#formatRate}
 * rather than a formatter of its own: two authorities for how a rate is spelled is how a summary
 * and a node label start disagreeing about the same flow.
 */
public final class BalanceView {

    private BalanceView() {}

    /** Which side of the chart's boundary a flow sits on, and whether the solver asked for it. */
    public enum Kind {
        /**
         * A gated sink: surplus leaving through a wired port because nothing downstream wanted it
         * all. Deliberately NOT called voided - nothing destroys it, and a player pulls it out of
         * the bus like any other output. It differs from {@link #PRODUCT} only in leaving through a
         * port that has an edge on it, which is why it is worth pointing at and not worth alarming
         * anyone about.
         */
        EXCESS,
        /** A gated source: a shortfall the solver had to invent, injected at this port. */
        IMPORT,
        /** An unwired output: what the chart is for. */
        PRODUCT,
        /** An unwired input: what the chart runs on. */
        SUPPLY
    }

    /**
     * One flow crossing the chart's edge, named by the port it crosses at.
     *
     * @param label ready to draw, e.g. {@code "excess 5.00mB/s Beta Ingot"} once rendered.
     */
    public record Boundary(PortRef port, Kind kind, double ratePerSecond, String ingredient, Note label) {}

    /**
     * Everything crossing the chart's boundary. Reads the balance already on hand and runs no
     * solver of its own, but still builds a list and a label per flow, so callers drawing every
     * frame want {@link Graph#boundary()} rather than this.
     */
    public static List<Boundary> boundary(final Graph graph) {
        final BalanceResult balance = graph.balance();
        if (!(balance instanceof final BalanceResult.Solved solved)) return configuredBoundary(graph);
        final SolutionView auto = solved.auto();
        final List<Boundary> out = new ArrayList<>();
        collect(graph, auto.gatedSinks, Kind.EXCESS, SolverMessage.BOUNDARY_EXCESS, out);
        collect(graph, auto.gatedSources, Kind.IMPORT, SolverMessage.BOUNDARY_ADD, out);
        collect(graph, auto.terminalOutputs, Kind.PRODUCT, SolverMessage.BOUNDARY_FLOW, out);
        collect(graph, auto.terminalInputs, Kind.SUPPLY, SolverMessage.BOUNDARY_FLOW, out);
        return List.copyOf(out);
    }

    /**
     * The no-solve image of the boundary: every unwired port drawn at the rate its configured
     * machine count implies. NONE mode and a stalled solve have no answer to read, but the chart
     * still says what it runs on and what it makes - an empty canvas reads as "nothing crosses
     * the boundary", which is a claim about the solver, not about the chart. Gated flows (excess,
     * imports) need a solve and are simply absent here.
     */
    private static List<Boundary> configuredBoundary(final Graph graph) {
        final Set<PortRef> wired = new HashSet<>();
        for (final Edge edge : graph.getEdges()
            .values()) {
            wired.add(new PortRef(edge.sourceNodeId, edge.sourceOutputIndex, false));
            wired.add(new PortRef(edge.targetNodeId, edge.targetInputIndex, true));
        }
        final List<Boundary> out = new ArrayList<>();
        for (final Node node : graph.getNodes()
            .values()) {
            collectConfigured(node, wired, false, out);
            collectConfigured(node, wired, true, out);
        }
        return List.copyOf(out);
    }

    /** One {@link Boundary} per unwired port of {@code node}, rated by its configured count. */
    private static void collectConfigured(final Node node, final Set<PortRef> wired, final boolean input,
        final List<Boundary> out) {
        final MachineConfig cfg = node.getMachineConfig();
        final double count = cfg.getMachineCount();
        if (count <= 0) return;
        final var eff = cfg.computeEffect(node.getProperties());
        final int durTicks = Math.max(1, eff.durationTicks());
        final int tf = eff.throughputFactor();
        final List<Port<?>> ports = input ? node.getInputs() : node.getOutputs();
        for (int i = 0; i < ports.size(); i++) {
            final Port<?> port = ports.get(i);
            if (wired.contains(new PortRef(node.getId(), i, input))) continue;
            final double qty = Math.max(0, port.getAmount()) * port.getChance()
                * (input ? cfg.inputMultiplier(i) : cfg.outputMultiplier(i))
                * tf;
            if (qty <= 0) continue;
            final double rate = count * qty * GuiHelper.TICKS_PER_SECOND / (double) durTicks;
            out.add(
                new Boundary(
                    new PortRef(node.getId(), i, input),
                    input ? Kind.SUPPLY : Kind.PRODUCT,
                    rate,
                    port.getDisplayName(),
                    SolverMessage.BOUNDARY_FLOW.toNote(
                        port.getType()
                            .formatAmount((float) rate) + "/s "
                            + port.getDisplayName())));
        }
    }

    /**
     * The answers this chart could equally well have had, as the summary panel's {@code CHOICES}
     * rows: one choice per answer plus a decision heading where the chart poses more than one
     * question. Reads the alternatives already handed over by the solve and rebuilds every row in
     * {@link #sortedRows} order. This is the single channel the panel reads; the old grouped
     * {@code Choices} view is gone because recompute() stores the complete flags itself.
     */
    public static List<Summary.Line<?>> toLineChoices(final Graph graph, final Alternatives alternatives) {
        if (alternatives.options()
            .isEmpty()) return List.of();
        // Grouped by the decision each option answers, in the order the solver emitted them, so a
        // chart posing two questions shows two short lists instead of one list of everything.
        final Map<PortRef, List<Alternative>> byDecision = new LinkedHashMap<>();
        final Map<PortRef, Note> headings = new LinkedHashMap<>();
        for (final Alternative option : alternatives.options()) {
            byDecision.computeIfAbsent(option.replaces(), k -> new ArrayList<>())
                .add(option);
            if (option.isCurrent()) headings.put(option.replaces(), describe(graph, option));
        }
        final List<List<Alternative>> decisions = new ArrayList<>();
        for (final List<Alternative> options : byDecision.values()) {
            // A decision with only its current answer under it is not a decision. Showing it would
            // put a heading above a row that repeats the heading, and imply a choice that is not
            // being offered.
            if (options.size() < 2) continue;
            decisions.add(options);
        }
        if (decisions.isEmpty()) return List.of();
        final List<Summary.Line<?>> out = new ArrayList<>(decisions.size() * 3);
        for (final List<Alternative> options : decisions) {
            if (decisions.size() > 1) {
                out.add(
                    new Summary.Line.Heading(
                        headings.getOrDefault(
                            options.getFirst()
                                .replaces(),
                            SolverMessage.BOUNDARY_NOTHING.toNote())));
            }
            for (final Alternative option : sortedRows(options)) {
                out.add(
                    new Summary.Line.Choice(
                        option.key(),
                        describe(graph, option),
                        option.toNote(),
                        option.isCurrent()));
            }
        }
        return out;
    }

    /**
     * Reading order for one decision's answers: the one on screen first, since every other row is
     * read against it, then the rest as a list of amounts - imports before surpluses, each
     * ascending. Deliberately not the solver's preference order.
     */
    private static List<Alternative> sortedRows(final List<Alternative> options) {
        final List<Alternative> sorted = new ArrayList<>(options);
        sorted.sort(
            Comparator.comparing((final Alternative o) -> !o.isCurrent())
                .thenComparing(o -> !isImport(o))
                .thenComparingDouble(BalanceView::crossingRate)
                .thenComparing(Alternative::key));
        return sorted;
    }

    /** Whether this answer brings the ingredient in, as opposed to letting a surplus out. */
    private static boolean isImport(final Alternative option) {
        return !option.externals()
            .isEmpty() && option.externals()
                .getFirst()
                .port()
                .input();
    }

    /** How much crosses the boundary under this answer, summed over the ports it uses. */
    private static double crossingRate(final Alternative option) {
        double rate = 0;
        for (final External e : option.externals()) {
            rate += e.ratePerSecond();
        }
        return rate;
    }

    private static void collect(final Graph graph, final List<External> externals, final Kind kind,
        final SolverMessage message, final List<Boundary> out) {
        for (final External e : externals) {
            final String ingredient = ingredientOf(graph, e);
            if (ingredient == null) continue;
            out.add(
                new Boundary(
                    e.port(),
                    kind,
                    e.ratePerSecond(),
                    ingredient,
                    message.toNote(rateOf(graph, e) + " " + ingredient)));
        }
    }

    /** "excess 5.00/s Beta Ingot" - what this option does at the one port that distinguishes it. */
    private static Note describe(final Graph graph, final Alternative option) {
        // One gate can cross at several ports of the same ingredient; "75/s water, 90/s water" is
        // two ports, not two decisions, so it reads as one number.
        final Map<String, double[]> merged = new LinkedHashMap<>();
        External sample = null;
        for (final External e : option.externals()) {
            final String ingredient = ingredientOf(graph, e);
            if (ingredient == null) continue;
            if (sample == null) sample = e;
            merged.computeIfAbsent(ingredient, k -> new double[1])[0] += e.ratePerSecond();
        }
        if (merged.isEmpty()) {
            return SolverMessage.BOUNDARY_NOTHING.toNote();
        }
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, double[]> entry : merged.entrySet()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(rateOf(graph, sample, entry.getValue()[0]))
                .append(' ')
                .append(entry.getKey());
        }
        final SolverMessage message = option.externals()
            .getFirst()
            .port()
            .input() ? SolverMessage.BOUNDARY_ADD : SolverMessage.BOUNDARY_EXCESS;
        return message.toNote(sb.toString());
    }

    /**
     * A rate spelled the way the summary panel spells it, units and all: the ingredient's own
     * formatter decides whether 530 reads as "530" or "530mB", and a chip that disagrees with the
     * summary about the same flow is worse than a chip with no units at all.
     */
    private static String rateOf(final Graph graph, final External external) {
        return rateOf(graph, external, external.ratePerSecond());
    }

    /** As above, but for a rate summed over several ports that share one ingredient. */
    private static String rateOf(final Graph graph, final External at, final double rate) {
        final Port<?> port = portOf(graph, at);
        return (port == null ? GuiHelper.formatRate((float) rate)
            : port.getType()
                .formatAmount((float) rate))
            + "/s";
    }

    /**
     * The ingredient at an external's port, or null when the port no longer exists - a recipe can
     * lose a slot between a solve and a redraw, and a missing name is a row to skip rather than a
     * crash.
     */
    private static String ingredientOf(final Graph graph, final External external) {
        final Port<?> port = portOf(graph, external);
        return port == null ? null : port.getDisplayName();
    }

    private static Port<?> portOf(final Graph graph, final External external) {
        final Node node = graph.getNodes()
            .get(
                external.port()
                    .nodeId());
        if (node == null) return null;
        final List<Port<?>> ports = external.port()
            .input() ? node.getInputs() : node.getOutputs();
        final int index = external.port()
            .portIndex();
        return index < 0 || index >= ports.size() ? null : ports.get(index);
    }
}
