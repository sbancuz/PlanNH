package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceView;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.data.flowchart.balancer.ChoiceKey;
import com.sbancuz.plannh.data.flowchart.balancer.Note;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Alternatives;
import com.sbancuz.plannh.data.properties.ResourceProperty;
import com.sbancuz.plannh.data.properties.SummaryProperty;
import com.sbancuz.plannh.gui.GuiHelper;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
public final class Summary extends GraphData {

    public static final String TYPE = "summary";

    public enum Section {

        ALL("plannh.summary.title.summary"),
        OUTPUTS("plannh.summary.title.outputs"),
        INPUTS("plannh.summary.title.inputs"),
        CHOICES("plannh.summary.title.choices"),
        PROPERTIES("plannh.summary.title.properties"),
        MACHINE_COUNTS("plannh.summary.title.machine_counts"),
        MESSAGES("plannh.summary.title.messages"),
        HELP("plannh.summary.title.help");

        public static final Section[] VALUES = Section.values();

        private final String titleKey;

        Section(final String titleKey) {
            this.titleKey = titleKey;
        }

        /** Localization key for the header's name; the GUI resolves it at draw time. */
        public String titleKey() {
            return titleKey;
        }
    }

    /** The panel-wide display mode: aligned per-cycle totals or per-second rates. */
    public enum Mode {
        CYCLES,
        THROUGHPUT
    }

    /**
     * The time unit per-second rates are spelled in. {@code secondsPerUnit} rescales a rate that
     * is stored per second; the lang keys cover the button's short form and the row suffix.
     */
    public enum RateUnit {

        SECONDS("second", 1),
        MINUTES("minute", 60),
        HOURS("hour", 60 * 60),
        DAYS("day", 60 * 60 * 24);

        public static final RateUnit[] VALUES = RateUnit.values();

        public final String langKey;
        public final double secondsPerUnit;

        RateUnit(final String name, final double secondsPerUnit) {
            this.langKey = "plannh.summary.rate." + name;
            this.secondsPerUnit = secondsPerUnit;
        }

        public String suffixKey() {
            return langKey + ".suffix";
        }
    }

    /**
     * One row the panel can draw. Minecraft-free: only {@link #displayName()} ever localizes, so
     * recompute() (which sorts the amount-bearing {@link Measure} rows headlessly) never touches a
     * formatter.
     */
    public sealed interface Line<T> permits Line.Measure,Line.Message,Line.Text,Line.Choice,Line.Heading,Line.Totals {

        /** Localized display text; the GUI is the only caller. */
        String displayName();

        /**
         * A measured row - a resource flow or a machine count. The label decides how the resource
         * is spelled and how the amount is formatted; the GUI just joins the two. {@code amount}
         * is the component's own - the sort key of the amount-sorted sections, which contain only
         * Measure rows - and lives here rather than on the interface.
         */
        record Measure<T> (SummaryProperty<T> label, T resource, float amount) implements Line<T> {

            @Override
            public String displayName() {
                return label.formatDisplayName(resource);
            }

            public String displayAmount(final float value) {
                return label.formatAmount(value);
            }
        }

        /** Something the solver said, coloured by severity. */
        record Message(Note note) implements Line<Object> {

            @Override
            public String displayName() {
                return note.render();
            }
        }

        /** A static help row; the string is a localization key the GUI resolves at draw time. */
        record Text(String text) implements Line<Object> {

            @Override
            public String displayName() {
                return text;
            }
        }

        /**
         * One answer on offer for a decision. {@code active} marks the answer on screen, {@code
         * reason} what it gives up against it (null for the default), shown on hover.
         */
        record Choice(ChoiceKey key, Note label, @Nullable Note reason, boolean active) implements Line<Object> {

            @Override
            public String displayName() {
                return label.render();
            }
        }

        /** A decision's title row, drawn only when the chart poses more than one decision. */
        record Heading(Note text) implements Line<Object> {

            @Override
            public String displayName() {
                return text.render();
            }
        }

        /**
         * The chart-wide run totals: how many operations and how long one cycle takes. Deliberately
         * not a Measure - it has no per-row amount to sort by, and the GUI formats and localizes the
         * sentence from these two raw numbers, so the model never builds display text. It is appended
         * after the sorted machine rows and always sits at the bottom of the section.
         */
        record Totals(double operations, int durationTicks) implements Line<Object> {

            @Override
            public String displayName() {
                return ""; // the GUI renders this row itself; displayName is never shown
            }
        }
    }

    /**
     * One {@link Line} per section, in the order the panel reads them. recompute() rebuilds the
     * content sections on every fresh solve; HELP is seeded at construction and never cleared.
     */
    transient private final Map<Section, List<Line<?>>> lines = new EnumMap<>(Section.class);

    /**
     * Which sections of this chart's panel the user has folded away, as one bit per {@link
     * Section} ordinal - {@link Section#ALL} the panel's own master collapse included. A single
     * integer, kept and written as-is by the plan serializer.
     */
    private int collapsedSummaryFolds = 0;

    @Getter
    @Nullable
    @Accessors(fluent = true)
    transient private BalanceResult balance = null;

    /** Whether the choice enumeration ran its full budget; false means the list is truncated. */
    @Getter
    transient private boolean choicesComplete = false;

    /** Why the choice list is as it is (budget, caps); shown over the CHOICES header. */
    @Getter
    transient private List<Note> choiceNotes = List.of();

    @Getter
    @Setter
    private ChoiceKey excessChoice;

    @Getter
    private Mode mode = Mode.CYCLES;
    @Getter
    private RateUnit rateUnit = RateUnit.SECONDS;
    private int[] sectionOrder = defaultSectionOrder();

    /**
     * Bumped by every settings change; part of the derived-cache key so a toggle
     * invalidates the rows without touching any {@code graph.version()}.
     */
    private transient long settingsVersion = 0;

    transient private long atVersion = -1;

    /** The {@link Graph} the current rows were derived for; a switch re-derives. */
    transient private Graph atGraph = null;

    /** The {@link Mode} the current {@link #lines} were derived for; a switch re-derives. */
    transient private Mode atMode = null;

    /** The {@code settingsVersion} the current rows were derived under. */
    transient private long atSettings = -1;

    /** Where a fresh chart's summary panel starts; kept in the GraphData so the spot is per-chart. */
    public static final int DEFAULT_X = 210;
    public static final int DEFAULT_Y = 46;

    public Summary() {
        super(UUID.randomUUID());
        x = DEFAULT_X;
        y = DEFAULT_Y;
        lines.put(
            Section.HELP,
            new ArrayList<>(
                List.of(
                    new Line.Text("plannh.summary.help.zoom"),
                    new Line.Text("plannh.summary.help.move"),
                    new Line.Text("plannh.summary.help.nei"),
                    new Line.Text("plannh.summary.help.add_recipe"))));
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public void setMode(final Mode mode) {
        this.mode = mode;
        settingsVersion++;
    }

    public void setRateUnit(final RateUnit rateUnit) {
        this.rateUnit = rateUnit;
        settingsVersion++;
    }

    public void setSectionOrder(final int[] sectionOrder) {
        this.sectionOrder = sectionOrder;
        settingsVersion++;
    }

    /**
     * The sanitized display order; a corrupt or missing array (old saves) repairs to the default
     * in place, so callers can keep the returned reference.
     */
    public int[] getSectionOrder() {
        final int n = Section.VALUES.length;
        boolean valid = sectionOrder != null && sectionOrder.length == n;
        if (valid) {
            final boolean[] seen = new boolean[n];
            for (final int ordinal : sectionOrder) {
                if (ordinal < 0 || ordinal >= n || seen[ordinal]) {
                    valid = false;
                    break;
                }
                seen[ordinal] = true;
            }
        }
        if (!valid) sectionOrder = defaultSectionOrder();
        return sectionOrder;
    }

    private static int[] defaultSectionOrder() {
        return Arrays.stream(Section.VALUES)
            .mapToInt(Section::ordinal)
            .toArray();
    }

    public long getSettingsVersion() {
        return settingsVersion;
    }

    public List<Line<?>> lines(final Section section) {
        return lines.getOrDefault(section, List.of());
    }

    /** Row count a section body has to fit; 0 for ALL, which holds no content. */
    public int lineCount(final Section section) {
        return section == Section.ALL ? 0 : lines(section).size();
    }

    /** The graph version this cache was derived from; moves with every choice click. */
    public long calculatedAt() {
        return atVersion;
    }

    /** The {@link Mode} the current rows were derived for; the panel pings it to reload. */
    public Mode computedMode() {
        return atMode;
    }

    /** Whether the section is currently folded away in this chart's panel. */
    public boolean isSummaryFold(final Section section) {
        return (collapsedSummaryFolds & (1 << section.ordinal())) != 0;
    }

    /** Folds or unfolds the section in this chart's panel. Pure UI state; no version bump. */
    public void setSummaryFold(final Section section, final boolean folded) {
        final int bit = 1 << section.ordinal();
        if (folded) collapsedSummaryFolds |= bit;
        else collapsedSummaryFolds &= ~bit;
    }

    /**
     * A machine-count row's label echoes the machine name itself; the amount is the operation
     * count spelled the way every machine count is. Never a key into the registry - the row says
     * "×12 Assembling Machine", and the name IS the resource.
     */
    private static final SummaryProperty<String> MACHINE_LABEL = SummaryProperty.<String>builder("machine_count", "")
        .displayFormatter(v -> v)
        .amountFormatter(GuiHelper::formatCount)
        .build();

    /**
     * Re-derive this summary's lines from a fresh balance: every node's throughput is scaled up to
     * the longest recipe on the chart (one cycle's rates) or to a per-second rate, per this
     * summary's {@link Mode}.
     */
    public Summary recompute(final Graph graph) {
        if (atGraph == graph && atVersion >= graph.version() && atMode == mode && atSettings == settingsVersion)
            return this;
        atGraph = graph;
        atMode = mode;
        atSettings = settingsVersion;

        this.balance = Balancer.balance(graph, graph.getBalanceMode());

        final Map<LineKey, Float> outputs = new HashMap<>();
        final Map<LineKey, Float> inputs = new HashMap<>();
        final Map<LineKey, Float> properties = new HashMap<>();

        final int cycleTicks = cycleTicks(balance, graph);

        // Accumulate scaled flows per resource across all ports, connected and unconnected alike.
        for (final Node node : graph.getNodes()) {
            final var nb = balance.nodeBalances()
                .get(node.id);
            if (nb == null) continue;

            final float scale = mode == Mode.THROUGHPUT
                ? (float) GuiHelper.TICKS_PER_SECOND / Math.max(1, nb.durationPerOp())
                : (float) cycleTicks / Math.max(1, nb.durationPerOp());
            accumulate(node.outputs, nb.effectiveOutputs(), scale, outputs);
            accumulate(node.inputs, nb.effectiveInputs(), scale, inputs);
        }

        net(inputs, outputs);
        accumulateProperties(balance, properties);

        // Every ordering decision lives here; the panel never re-sorts. Outputs lead with the
        // headline product, inputs with the scarcest ingredient, properties and machine counts
        // alike. Name breaks ties, or equal flows shuffle between frames. The chart-wide totals
        // line is appended after the sorted machine rows so it always closes the section.
        setLines(Section.OUTPUTS, sortDesc(flatten(outputs)));
        setLines(Section.INPUTS, sortAsc(flatten(inputs)));
        setLines(Section.PROPERTIES, sortAsc(flatten(properties)));
        final List<Line<?>> machineLines = sortDesc(machineLines(graph));
        if (balance.totalOperations() > 0) {
            machineLines.add(new Line.Totals(balance.totalOperations(), balance.totalDurationTicks()));
        }
        setLines(Section.MACHINE_COUNTS, machineLines);
        setLines(Section.CHOICES, choiceLines(graph));
        setLines(Section.MESSAGES, messageLines());

        atVersion = graph.version();
        return this;
    }

    /** The longest recipe on the chart, so one cycle of every machine aligns; 20 ticks when none ran. */
    private static int cycleTicks(final BalanceResult balance, final Graph graph) {
        int maxTicks = 0;
        for (final Node node : graph.getNodes()) {
            final var nb = balance.nodeBalances()
                .get(node.id);
            if (nb != null) maxTicks = Math.max(maxTicks, nb.durationPerOp());
        }
        return Math.max(maxTicks, 20);
    }

    /** Fold each port's effective flow into {@code into}, scaled to one chart cycle. */
    private static void accumulate(final List<Port<?>> ports, final Map<Integer, Float> totals, final float scale,
        final Map<LineKey, Float> into) {
        for (int i = 0; i < ports.size(); i++) {
            final Float total = totals.get(i);
            if (total == null || total <= 0) continue;
            into.merge(LineKey.ResourceKey.of(ports.get(i)), total * scale, Float::sum);
        }
    }

    /**
     * Net by resource: output = max(0, prod - cons), input = max(0, cons - prod). The tolerance has
     * to clear the accumulated float error, not one float's worth of it: these are sums over every
     * port carrying the resource, so error grows with the number of contributors, and at 1e-6 a
     * fully recycled ingredient on a large chart prints a ghost line for a rate that is really zero.
     */
    private static void net(final Map<LineKey, Float> inputs, final Map<LineKey, Float> outputs) {
        final Map<LineKey, Float> netInputs = new HashMap<>();
        for (final var entry : inputs.entrySet()) {
            final LineKey key = entry.getKey();
            final float cons = entry.getValue();
            final float prod = outputs.getOrDefault(key, 0f);
            final float eps = Math.max(prod, cons) * 1e-4f;
            if (Math.abs(cons - prod) <= eps) {
                outputs.remove(key);
            } else if (cons > prod) {
                netInputs.put(key, cons - prod);
                outputs.remove(key);
            } else {
                outputs.put(key, prod - cons);
            }
        }
        inputs.clear();
        inputs.putAll(netInputs);
    }

    private static void accumulateProperties(final BalanceResult balance, final Map<LineKey, Float> into) {
        for (final var entry : balance.propertyTotals()
            .entrySet()) {
            if (!(entry.getKey() instanceof SummaryProperty<?>prop)) continue;
            into.merge(new LineKey.PropertyKey(prop), (float) entry.getValue(), Float::sum);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Line<?>> flatten(final Map<LineKey, Float> map) {
        final List<Line<?>> result = new ArrayList<>();
        for (final var entry : map.entrySet()) {
            if (entry.getValue() <= 0) continue;
            final Line<?> line = switch (entry.getKey()) {
                case LineKey.ResourceKey rk -> rk.toLine(entry.getValue());
                case LineKey.PropertyKey pk -> new Line.Measure(pk.prop(), pk.prop()
                    .getDefaultValue(), entry.getValue());
            };
            result.add(line);
        }
        return result;
    }

    /** One Measure row per machine with work to do; the chart-wide totals line is appended after. */
    private List<Line<?>> machineLines(final Graph graph) {
        final List<Line<?>> out = new ArrayList<>();
        for (final Node node : graph.getNodes()) {
            final var nb = balance.nodeBalances()
                .get(node.id);
            if (nb == null) continue;
            final double ops = nb.operations();
            if (ops <= 0) continue;
            out.add(new Line.Measure<>(MACHINE_LABEL, node.machineName, (float) ops));
        }
        return out;
    }

    /**
     * The answers the chart could equally well have had. Built from the solve's own alternatives -
     * the same pass that produced the balance, so asking costs nothing - and stored as rows the
     * panel reads without running a solver of its own. No solved balance, no gates, or no offer on
     * any decision leaves an empty section, which the widget draws as nothing at all.
     */
    private List<Line<?>> choiceLines(final Graph graph) {
        if (!(balance instanceof final BalanceResult.Solved solved) || solved.alternatives() == null) {
            choicesComplete = false;
            choiceNotes = List.of();
            return List.of();
        }
        final Alternatives alternatives = solved.alternatives();
        choicesComplete = alternatives.complete();
        choiceNotes = alternatives.notes();
        return solved.auto().openGates > 0 ? BalanceView.toLineChoices(graph, alternatives) : List.of();
    }

    /** Everything the solver had to say, at every severity, in solver order. */
    private List<Line<?>> messageLines() {
        final List<Line<?>> out = new ArrayList<>();
        for (final Note note : balance.notes()) out.add(new Line.Message(note));
        return out;
    }

    /** Replace a section's content wholesale; HELP is never touched here. */
    private void setLines(final Section section, final List<Line<?>> sectionLines) {
        lines.computeIfAbsent(section, k -> new ArrayList<>())
            .clear();
        lines.get(section)
            .addAll(sectionLines);
    }

    /**
     * The amount-sorted sections (outputs, inputs, properties, machine counts) hold only Measure
     * rows by construction, so the sort reads the amount from a Measure directly.
     */
    @SuppressWarnings("unchecked")
    private static List<Line<?>> sortDesc(final List<Line<?>> lines) {
        lines.sort(
            Comparator.comparingDouble((Line<?> l) -> ((Line.Measure<Object>) l).amount())
                .reversed()
                .thenComparing(Line::displayName));
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static List<Line<?>> sortAsc(final List<Line<?>> lines) {
        lines.sort(
            Comparator.comparingDouble((Line<?> l) -> ((Line.Measure<Object>) l).amount())
                .thenComparing(Line::displayName));
        return lines;
    }

    @SuppressWarnings("rawtypes")
    private sealed interface LineKey permits LineKey.ResourceKey,LineKey.PropertyKey {

        record ResourceKey<T> (ResourceProperty<T> type, T resource) implements LineKey {

            @SuppressWarnings("unchecked")
            static ResourceKey<Object> of(final Port port) {
                return new ResourceKey<>((ResourceProperty<Object>) port.getType(), port.getValue());
            }

            Line<?> toLine(final float amount) {
                return new Line.Measure<>(type, resource, amount);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean equals(final Object o) {
                return o instanceof ResourceKey<?>(SummaryProperty<?> type1, Object resource1)
                    && type == type1
                    && type.canConnect(resource, (T) resource1);
            }

            @Override
            public int hashCode() {
                return type.hashValue(resource);
            }
        }

        record PropertyKey(SummaryProperty<?> prop) implements LineKey {}
    }
}
