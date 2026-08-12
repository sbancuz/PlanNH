package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.Locale;

import net.minecraft.util.StatCollector;

/**
 * The single authority for what the balancer says. Every note, error, preference name, rank reason
 * and boundary label is one constant here - the {@link Severity} the panel colours it by and the key
 * it is localized under (resolved with {@link StatCollector} by {@link #render}). A concrete
 * utterance is a {@link Note} built with {@link #toNote}: the constant names WHAT was said, the
 * arguments carry the runtime data (machine names, ingredients, rates) it was said about.
 */
public enum SolverMessage {

    // -----------------------------------------------------------------------------------------
    // Failures - why a solve produced no point
    // -----------------------------------------------------------------------------------------
    NO_PIN(Severity.INFO, "plannh.solver.no_pin"),
    EMPTY_GRAPH(Severity.ERROR, "plannh.solver.empty_graph"),
    BALANCE_FAILED(Severity.ERROR, "plannh.solver.balance_failed"),
    VALIDATION_FAILED(Severity.ERROR, "plannh.solver.validation_failed"),

    // -----------------------------------------------------------------------------------------
    // Wiring diagnostics - smells the solver raises against the chart as drawn
    // -----------------------------------------------------------------------------------------
    WIRING_IMPORT(Severity.INFO, "plannh.solver.wiring_import"),
    WIRING_UNLINKED(Severity.WARN, "plannh.solver.wiring_unlinked"),
    OVERSHOOTS_TARGET(Severity.WARN, "plannh.solver.overshoots_target"),

    // -----------------------------------------------------------------------------------------
    // The alternatives search - the honesty notes a truncated or overridden answer owes the reader
    // -----------------------------------------------------------------------------------------
    SHOWING_CLOSEST(Severity.INFO, "plannh.solver.showing_closest"),
    STOPPED_EARLY(Severity.INFO, "plannh.solver.stopped_early"),
    CHOICE_NO_LONGER_FITS(Severity.WARN, "plannh.solver.choice_no_longer_fits"),
    CHOICE_NEEDS_MORE_GATES(Severity.WARN, "plannh.solver.choice_needs_more_gates"),

    // -----------------------------------------------------------------------------------------
    // Pipeline diagnostics - the solver explaining a rejection or a conflict
    // -----------------------------------------------------------------------------------------
    PIN_CONFLICT(Severity.ERROR, "plannh.solver.pins_conflict"),
    STAGE_FAILED(Severity.ERROR, "plannh.solver.stage_failed"),
    MACHINES_CANNOT_RUN(Severity.ERROR, "plannh.solver.machines_cannot_run"),
    SOLVER_BUDGET(Severity.ERROR, "plannh.solver.budget"),
    SOLVER_UNSATISFIABLE(Severity.ERROR, "plannh.solver.unsatisfiable"),
    SOLVER_NO_SOLUTION(Severity.ERROR, "plannh.solver.no_solution"),
    SOLVER_NOT_CONSERVING(Severity.ERROR, "plannh.solver.not_conserving"),
    SOLVER_NEGATIVE_EXTENT(Severity.ERROR, "plannh.solver.negative_extent"),
    SOLVER_NEGATIVE_FLOW(Severity.ERROR, "plannh.solver.negative_flow"),
    SOLVER_INEXACT_RESIDUAL(Severity.ERROR, "plannh.solver.inexact_residual"),
    SOLVER_UNDER_SUPPLY(Severity.ERROR, "plannh.solver.under_supply"),
    GATE_COUNT_NOT_CERTIFIED(Severity.INFO, "plannh.solver.gate_count_not_certified"),

    // -----------------------------------------------------------------------------------------
    // Names - pin kinds and the preferences that rank answers
    // -----------------------------------------------------------------------------------------
    PIN_EXTENT(Severity.INFO, "plannh.solver.pin_extent"),
    PIN_TARGET(Severity.INFO, "plannh.solver.pin_target"),
    PIN_COUNT(Severity.INFO, "plannh.solver.pin_count"),
    PREF_FEWEST_GATES(Severity.INFO, "plannh.solver.pref_fewest_gates"),
    PREF_FEWEST_IMPORTS(Severity.INFO, "plannh.solver.pref_fewest_imports"),
    PREF_LEAST_EXCESS(Severity.INFO, "plannh.solver.pref_least_excess"),
    PREF_LEAST_FLOW(Severity.INFO, "plannh.solver.pref_least_flow"),

    // -----------------------------------------------------------------------------------------
    // Rank reasons - why an answer is not the default
    // -----------------------------------------------------------------------------------------
    REASON_EQUALLY_VALID(Severity.INFO, "plannh.solver.reason_equally_valid"),
    REASON_IMPORTS_INSTEAD(Severity.INFO, "plannh.solver.reason_imports_instead"),
    REASON_MOVES_MORE(Severity.INFO, "plannh.solver.reason_moves_more"),
    REASON_MOVES_LESS(Severity.INFO, "plannh.solver.reason_moves_less"),
    REASON_VOIDS_MORE(Severity.INFO, "plannh.solver.reason_voids_more"),
    REASON_CROSSES_MORE(Severity.INFO, "plannh.solver.reason_crosses_more"),
    REASON_IMPORTS_MORE(Severity.INFO, "plannh.solver.reason_imports_more"),
    REASON_LEAVES_EXCESS(Severity.INFO, "plannh.solver.reason_leaves_excess"),

    // -----------------------------------------------------------------------------------------
    // Boundary labels - what crosses the chart's edge
    // -----------------------------------------------------------------------------------------
    BOUNDARY_EXCESS(Severity.INFO, "plannh.solver.boundary_excess"),
    BOUNDARY_ADD(Severity.INFO, "plannh.solver.boundary_add"),
    BOUNDARY_FLOW(Severity.INFO, "plannh.solver.boundary_flow"),
    BOUNDARY_NOTHING(Severity.INFO, "plannh.solver.boundary_nothing");

    private final Severity severity;
    private final String key;

    SolverMessage(final Severity severity, final String key) {
        this.severity = severity;
        this.key = key;
    }

    public Severity severity() {
        return severity;
    }

    /** The {@code plannh.solver.*} language-file key. */
    public String key() {
        return key;
    }

    /** One utterance of this message: the constant plus the data it was said about. */
    public Note toNote(final Object... args) {
        return new Note(this, args);
    }

    /**
     * The sentence through the language file, with {@code args} spliced into it. A missing
     * translation resolves to the bare key rather than to a crash. GUI only - this needs a live
     * {@link StatCollector}.
     */
    public String render(final Object... args) {
        final String local = StatCollector.translateToLocal(key);
        if (local.equals(key)) {
            return key;
        }
        return String.format(Locale.ROOT, local, args);
    }

    /** The key itself, Minecraft-free: the pipeline, profiler, logs and tests use this identity. */
    public String describe() {
        return key;
    }
}
