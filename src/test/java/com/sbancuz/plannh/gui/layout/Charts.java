package com.sbancuz.plannh.gui.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chart fixtures for the layout tests.
 *
 * <p>
 * Built rather than loaded, because these tests are about the engine's contract with the model — not
 * overlap, not reproducibility, not the corridor — and a builder says what is in the chart. The real
 * charts, and the saved chart from a player, are {@code ElkLayoutCorpusTest} and
 * {@code SavedChartLayoutTest}'s job; every bug that ever mattered in this area came from those, and
 * none of them came from here.
 *
 * <p>
 * Ids are derived from a seed rather than random, so a failure reads the same twice.
 */
public final class Charts {

    private static final int DEFAULT_PAD = 12;
    private static final int DEFAULT_HEADER = 20;

    private Charts() {}

    public static UUID id(final int seed) {
        return new UUID(0, seed);
    }

    /** A machine with a box and no pins; geometry most tests do not care about. */
    public static LayoutMachine machine(final int seed) {
        return machine(seed, 100, 80);
    }

    public static LayoutMachine machine(final int seed, final int width, final int height) {
        return new LayoutMachine(id(seed), "m" + seed, width, height);
    }

    public static LayoutGroup group(final int seed, final Integer... members) {
        final List<UUID> ids = new ArrayList<>();
        for (final Integer member : members) ids.add(id(member));
        return new LayoutGroup(id(seed), ids, DEFAULT_PAD, DEFAULT_HEADER);
    }

    public static LayoutRelation relation(final int seed, final int from, final int to) {
        return new LayoutRelation(id(seed), id(from), id(to));
    }

    public static LayoutNote note(final int seed, final int width, final int height, final LayoutNote.Side side,
        final UUID anchor) {
        return new LayoutNote(id(seed), width, height, side, anchor);
    }

    /** A straight chain of {@code length} machines, each feeding the next. */
    public static LayoutRequest chain(final int length) {
        final List<LayoutMachine> machines = new ArrayList<>();
        final List<LayoutRelation> relations = new ArrayList<>();
        for (int i = 0; i < length; i++) machines.add(machine(i + 1));
        for (int i = 0; i + 1 < length; i++) relations.add(relation(1000 + i, i + 1, i + 2));
        return new LayoutRequest(machines, relations, List.of(), List.of());
    }

    /** Settings with generous spacings, so a test's assertions are about the contract and not the packing. */
    public static LayoutSettings settings() {
        return new LayoutSettings(40, 120, 7, 300, 200);
    }
}
