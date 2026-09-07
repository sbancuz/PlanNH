package com.sbancuz.plannh.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The review column is the one thing in the machine table a regeneration cannot rebuild, so what
 * survives an overwrite and what does not is the contract worth pinning. The key is the machine's
 * numbers: identical numbers mean nothing a reviewer looked at has moved, whatever GregTech did.
 */
class ReviewTicksTest {

    private static final String HEADER = "| " + ReviewTicks.COLUMN + " | machine | numbers | probe |";
    private static final String SEPARATOR = "|---|---|---|---|";

    private static ReviewTicks of(final String... rows) {
        return ReviewTicks.fromLines(List.of(rows));
    }

    @Test
    void aTickSurvivesWhileTheImplementationIsUnchanged() {
        final ReviewTicks ticks = of(HEADER, SEPARATOR, "| ✅ | Industrial Centrifuge | par=8 dur=1/2 | agrees |");

        assertEquals("✅", ticks.forMachine("Industrial Centrifuge", "par=8 dur=1/2"));
    }

    /** The whole point of the fingerprint: GregTech moved, so nobody has reviewed what it does now. */
    @Test
    void aTickIsVoidedWhenTheImplementationMoves() {
        final ReviewTicks ticks = of(HEADER, SEPARATOR, "| ✅ | Industrial Centrifuge | par=8 dur=1/2 | agrees |");

        assertEquals(ReviewTicks.UNCHECKED, ticks.forMachine("Industrial Centrifuge", "par=6 dur=1/2"));
    }

    @Test
    void aMachineTheFileNeverHadStartsUnchecked() {
        final ReviewTicks ticks = of(HEADER, SEPARATOR, "| ✅ | Industrial Centrifuge | par=8 dur=1/2 | agrees |");

        assertEquals(ReviewTicks.UNCHECKED, ticks.forMachine("Large Chemical Reactor", "par=8 dur=1/2"));
    }

    /** Whatever mark the reviewer used is theirs; the column is not a fixed vocabulary. */
    @Test
    void anyMarkTheReviewerUsedIsCarriedBackVerbatim() {
        final ReviewTicks ticks = of(HEADER, SEPARATOR, "| checked 2026-09-07 | Pyrolyse Oven | par=1 | agrees |");

        assertEquals("checked 2026-09-07", ticks.forMachine("Pyrolyse Oven", "par=1"));
    }

    /** A header row has a data row's shape, so it has to be recognised rather than fallen through. */
    @Test
    void onlyDataRowsAreRead() {
        final ReviewTicks ticks = of(
            "# Multiblock config simplifier table",
            "- **numbers** - what a chart plans this machine with",
            HEADER,
            SEPARATOR,
            "| " + ReviewTicks.UNCHECKED + " | Pyrolyse Oven | par=1 | agrees |");

        assertEquals(ReviewTicks.UNCHECKED, ticks.forMachine("machine", "numbers"));
        assertEquals(ReviewTicks.UNCHECKED, ticks.forMachine("Pyrolyse Oven", "par=1"));
    }
}
