package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.layout.AutoLayout;
import com.sbancuz.plannh.layout.AutoLayout.LayoutNode;

/**
 * Boundary chips are drawn beside a node but are not nodes, so the layout has to be told to keep
 * room for them. ELK layered overwrites {@code CoreOptions.MARGINS} during placement, which made
 * the obvious way to ask for that room fail silently - the next column landed on top of the text.
 */
class LayoutMarginTest {

    private record N(UUID id, String machineName, int worldWidth, int worldHeight, int inputCount, int outputCount)
        implements LayoutNode {}

    private static final UUID A = UUID.nameUUIDFromBytes("a".getBytes());
    private static final UUID B = UUID.nameUUIDFromBytes("b".getBytes());
    private static final List<LayoutNode> NODES = List.of(new N(A, "a", 100, 60, 1, 1), new N(B, "b", 100, 60, 1, 1));
    private static final List<Edge> LINKS = List.of(new Edge(UUID.nameUUIDFromBytes("e".getBytes()), A, B, 0, 0));

    private static int gap(final Map<UUID, int[]> positions) {
        return positions.get(B)[0] - (positions.get(A)[0] + 100);
    }

    @Test
    void askedForSpaceIsActuallyAvailable() {
        // The contract is total clearance, not additional clearance: a label needing 200 units must
        // END UP with 200 units of gap to its neighbour. Asking for it on top of the corridor the
        // layout already leaves is how the columns ended up hundreds of units too far apart.
        final int bare = gap(AutoLayout.layout(NODES, LINKS));
        final int padded = gap(AutoLayout.layout(NODES, LINKS, Map.of(A, new int[] { 0, 200 })));

        assertTrue(padded >= 200, "a 200-unit label must fit, got a " + padded + " gap");
        assertTrue(padded > bare, "and it has to widen the gap at all, from " + bare);
    }

    @Test
    void clearanceIsNotPaidTwiceOver() {
        // The pathological case: both nodes either side of one corridor carry a label. Reserving
        // each in full would sum them; what is needed is enough room for both plus the corridor.
        final int both = gap(AutoLayout.layout(NODES, LINKS, Map.of(A, new int[] { 0, 200 }, B, new int[] { 200, 0 })));

        assertTrue(both >= 400, "both labels must fit, got " + both);
        assertTrue(both < 500, "but not with a whole extra corridor each, got " + both);
    }

    @Test
    void paddingMovesNeighboursAndNotTheNodeItself() {
        // The caller positions the machine, not the machine plus its labels: if the padding leaked
        // into the result, every node with a chip would sit displaced from where its edges land.
        final Map<UUID, int[]> bare = AutoLayout.layout(NODES, LINKS);
        final Map<UUID, int[]> padded = AutoLayout.layout(NODES, LINKS, Map.of(A, new int[] { 0, 200 }));

        assertEquals(bare.get(A)[0], padded.get(A)[0], "the padded node stays where it was");
        assertTrue(padded.get(B)[0] > bare.get(B)[0], "its neighbour is the one that moves");
    }

    @Test
    void aChartWithNoLabelsLaysOutExactlyAsBefore() {
        final Map<UUID, int[]> bare = AutoLayout.layout(NODES, LINKS);
        final Map<UUID, int[]> empty = AutoLayout.layout(NODES, LINKS, Map.of());

        assertEquals(bare.keySet(), empty.keySet());
        for (final UUID id : bare.keySet()) {
            assertArrayEquals(bare.get(id), empty.get(id), "positions must not move when nothing asks for room");
        }
    }
}
