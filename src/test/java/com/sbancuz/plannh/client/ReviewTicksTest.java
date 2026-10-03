package com.sbancuz.plannh.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The review column is the only part of the machine table a regeneration can't rebuild, so these tests pin what
 * survives an overwrite. The key is the machine's numbers: identical numbers mean nothing a reviewer looked at has
 * moved, whatever GregTech did.
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

    /** Changed numbers mean GregTech moved, so the old tick is void. */
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

    /** Any mark the reviewer used is returned verbatim. The column has no fixed vocabulary. */
    @Test
    void anyMarkTheReviewerUsedIsCarriedBackVerbatim() {
        final ReviewTicks ticks = of(HEADER, SEPARATOR, "| checked 2026-09-07 | Pyrolyse Oven | par=1 | agrees |");

        assertEquals("checked 2026-09-07", ticks.forMachine("Pyrolyse Oven", "par=1"));
    }

    /** A header row has a data row's shape, so it is matched by its heading and skipped. */
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
