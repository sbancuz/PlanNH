package com.sbancuz.plannh.gui.layout;

import static com.sbancuz.plannh.gui.layout.Charts.box;
import static com.sbancuz.plannh.gui.layout.Charts.chain;
import static com.sbancuz.plannh.gui.layout.Charts.group;
import static com.sbancuz.plannh.gui.layout.Charts.id;
import static com.sbancuz.plannh.gui.layout.Charts.machine;
import static com.sbancuz.plannh.gui.layout.Charts.relation;
import static com.sbancuz.plannh.gui.layout.Charts.settings;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cleanroommc.modularui.widget.sizer.Area;

import it.unimi.dsi.fastutil.ints.IntIntPair;

/**
 * The engine's contract with the chart model: everything gets placed, nothing lands on anything else,
 * and the answer does not depend on the order the builder walked the chart in.
 */
public class LayoutEngineTest {

    private final LayoutStrategy engine = new ElkLayoutStrategy();

    // ---------------------------------------------------------------------------------------
    // Placement and reproducibility
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("every machine is placed, and its producer sits left of its consumer")
    public void chainRunsLeftToRight() {
        final LayoutPlan plan = engine.layout(chain(5), settings());

        assertEquals(
            5,
            plan.machines()
                .size());
        for (int i = 1; i <= 4; i++) {
            final IntIntPair producer = plan.machines()
                .get(id(i));
            final IntIntPair consumer = plan.machines()
                .get(id(i + 1));
            assertNotNull(producer, "machine " + i + " was dropped");
            assertNotNull(consumer, "machine " + (i + 1) + " was dropped");
            assertTrue(
                producer.leftInt() < consumer.leftInt(),
                "a producer must sit left of its consumer, got " + producer + " then " + consumer);
        }
    }

    @Test
    @DisplayName("the same chart lays out identically every time")
    public void layoutIsReproducible() {
        final LayoutRequest request = chain(12);
        final LayoutPlan first = engine.layout(request, settings());

        for (int run = 0; run < 4; run++) {
            assertEquals(first, engine.layout(request, settings()), "run " + run + " differed");
        }
    }

    @Test
    @DisplayName("shuffling the request does not move anything")
    public void layoutIsIndependentOfInputOrder() {
        // The guarantee the whole ordering apparatus exists for: ELK consumes nodes in model order and
        // breaks ties on it, so without canonicalisation this moved every machine on the chart.
        final List<LayoutMachine> machines = new ArrayList<>();
        for (int i = 1; i <= 14; i++) machines.add(machine(i));
        final List<LayoutRelation> relations = List.of(
            relation(101, 1, 2),
            relation(102, 2, 3),
            relation(103, 4, 5),
            relation(104, 5, 6),
            relation(105, 7, 8),
            relation(106, 3, 9),
            relation(107, 9, 10),
            relation(108, 2, 11),
            relation(109, 12, 13));

        final LayoutPlan reference = engine
            .layout(new LayoutRequest(machines, relations, List.of(), List.of()), settings());

        for (int seed = 0; seed < 20; seed++) {
            final List<LayoutMachine> shuffled = new ArrayList<>(machines);
            Collections.shuffle(shuffled, new java.util.Random(seed));
            final LayoutPlan actual = engine
                .layout(new LayoutRequest(shuffled, relations, List.of(), List.of()), settings());
            assertEquals(reference, actual, "shuffle " + seed + " moved something");
        }
    }

    @Test
    @DisplayName("no two machines overlap")
    public void machinesDoNotOverlap() {
        final LayoutPlan plan = engine.layout(chain(16), settings());

        final List<UUID> ids = new ArrayList<>(
            plan.machines()
                .keySet());
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                final IntIntPair a = plan.machines()
                    .get(ids.get(i));
                final IntIntPair b = plan.machines()
                    .get(ids.get(j));
                assertFalse(
                    box(a, 100, 80).intersects(box(b, 100, 80)),
                    ids.get(i) + " at " + a + " overlaps " + ids.get(j) + " at " + b);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Relations
    //
    // A relation with a missing endpoint is dropped by CanonicalOrder and asserted in
    // LayoutOrderTreeTest, which tests the filter itself rather than the layout it enables. Only the
    // cases the engine must survive on its own are here.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a cycle is laid out rather than rejected")
    public void recycleLoopIsLaidOut() {
        // Every chart with a recycle loop has one, and the balancer's own corpus is full of them.
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2), machine(3)),
            List.of(relation(10, 1, 2), relation(11, 2, 3), relation(12, 3, 1)),
            List.of(),
            List.of());

        final LayoutPlan plan = assertDoesNotThrow(() -> engine.layout(request, settings()));

        assertEquals(
            3,
            plan.machines()
                .size());
    }

    // ---------------------------------------------------------------------------------------
    // Groups
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a group's members land inside its frame")
    public void membersLandInsideTheirFrame() {
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2), machine(3), machine(4)),
            List.of(relation(10, 1, 2), relation(11, 3, 4)),
            List.of(group(90, 2, 3)),
            List.of());

        final LayoutPlan plan = engine.layout(request, settings());
        final Area frame = plan.groupFrames()
            .get(id(90));

        assertNotNull(frame, "the group got no frame");
        for (final int member : new int[] { 2, 3 }) {
            final IntIntPair at = plan.machines()
                .get(id(member));
            assertNotNull(at, "group member " + member + " was dropped");
            assertTrue(
                frame.x <= at.leftInt() && at.leftInt() + 100 <= frame.ex(),
                "member " + member + " at " + at + " hangs off the left or right of " + frame);
            assertTrue(
                frame.y <= at.rightInt() && at.rightInt() + 80 <= frame.ey(),
                "member " + member + " at " + at + " hangs off the top or bottom of " + frame);
        }
    }

    @Test
    @DisplayName("a frame respects the group minimum even when its contents are tiny")
    public void frameRespectsTheMinimum() {
        // The engine reserves the frame as the compound's padding and reports the box ELK sized, so a
        // group holding one small machine still has to come out at least GROUP_MIN_W/H. An earlier
        // attempt compared that minimum against the padded footprint, and added the shortfall to the
        // wrong side, so it reserved space it never reserved.
        final LayoutSettings tight = new LayoutSettings(40, 120, 7, 300, 200);
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1)),
            List.of(),
            List.of(group(90, 1)),
            List.of());

        final Area frame = engine.layout(request, tight)
            .groupFrames()
            .get(id(90));

        assertNotNull(frame);
        assertTrue(frame.width >= 300 + 2 * 12, "frame is " + frame.width + " wide, minimum is 324");
        assertTrue(frame.height >= 200 + 2 * 12 + 20, "frame is " + frame.height + " tall, minimum is 244");
    }

    @Test
    @DisplayName("no loose machine lands inside a group frame")
    public void looseMachineStaysOutOfFrames() {
        // The bug that cost the previous attempt the most time: the engine returns the box holding a
        // group's contents, but the frame drawn is that box grown by a pad and a header, so a neighbour
        // placed clear of the box ended up under the frame.
        final List<LayoutMachine> machines = new ArrayList<>();
        for (int i = 1; i <= 12; i++) machines.add(machine(i));
        final List<LayoutRelation> relations = new ArrayList<>();
        for (int i = 1; i < 12; i++) relations.add(relation(500 + i, i, i + 1));

        final LayoutRequest request = new LayoutRequest(machines, relations, List.of(group(90, 5, 6)), List.of());

        final LayoutPlan plan = engine.layout(request, settings());
        final Area frame = plan.groupFrames()
            .get(id(90));

        assertNotNull(frame);
        for (final int loose : new int[] { 1, 2, 3, 4, 7, 8, 9, 10, 11, 12 }) {
            final IntIntPair at = plan.machines()
                .get(id(loose));
            assertFalse(
                box(at, 100, 80).intersects(frame),
                "loose machine " + loose + " at " + at + " landed inside the frame " + frame);
        }
    }

    @Test
    @DisplayName("every position in the plan is world, even two groups deep")
    public void nestedGroupsGetWorldPositions() {
        // The trap: ELK returns parent-relative coordinates for anything inside a compound, so a naive
        // extraction is right at the top level and wrong only for nested content - which reads as "the
        // nesting is slightly off" rather than as a bug. Asserted at depth two for that reason.
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2), machine(3), machine(4)),
            List.of(relation(10, 1, 2), relation(11, 4, 3)),
            List.of(group(90, 2, 91), group(91, 3, 4)),
            List.of());

        final LayoutPlan plan = engine.layout(request, settings());
        final Area outer = plan.groupFrames()
            .get(id(90));
        final Area inner = plan.groupFrames()
            .get(id(91));

        assertNotNull(outer, "the outer group got no frame");
        assertNotNull(inner, "the inner group got no frame");

        // A parent-relative inner frame would sit at or near the origin regardless of where the outer
        // group actually ended up.
        assertTrue(inner.x > outer.x, "inner at " + inner + " is not inside outer at " + outer);
        assertTrue(inner.y > outer.y, "inner at " + inner + " is not inside outer at " + outer);
        assertTrue(
            inner.ex() <= outer.ex() && inner.ey() <= outer.ey(),
            "inner " + inner + " is not enclosed by outer " + outer);

        for (final int member : new int[] { 3, 4 }) {
            final IntIntPair at = plan.machines()
                .get(id(member));
            assertTrue(
                at.leftInt() >= inner.x && at.leftInt() + 100 <= inner.ex(),
                "nested member " + member + " at " + at + " is outside the inner frame " + inner);
        }
        // And the loose machine must not have been dragged inside either frame.
        final IntIntPair loose = plan.machines()
            .get(id(1));
        assertFalse(box(loose, 100, 80).intersects(outer));
    }

    @Test
    @DisplayName("a relation crossing a group boundary places both ends")
    public void crossBoundaryRelationsWork() {
        // In, out, and group-to-group. An earlier attempt needed a port chain through every compound
        // ancestor to do this; plain node-to-node edges across the boundary suffice in all directions
        // when the routes are discarded, which they are.
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2), machine(3)),
            List.of(relation(10, 1, 2), relation(11, 2, 3)),
            List.of(group(90, 2)),
            List.of());

        final LayoutPlan plan = assertDoesNotThrow(() -> engine.layout(request, settings()));

        assertEquals(
            3,
            plan.machines()
                .size(),
            "a relation crossing a group boundary lost an endpoint");
    }

    /**
     * A group holding one machine must not become a compound.
     *
     * <p>
     * Found in game, on a four-machine fan: macerator -&gt; {shaped crafting, melting core, mixer}, with the
     * shaped-crafting machine alone in a group. As a compound, ELK treats that machine as living in a
     * hierarchy level, so the edges crossing the boundary pick up external-port dummies — and the group
     * and one of the machines it feeds end up in the <em>same layer</em>. The result is one column fewer
     * than the topology deserves and an arrow drawn straight down:
     *
     * <pre>
     * group as compound:  macerator | {shaped, melting core}    2 columns, one vertical arrow
     * frame on the box:   macerator | shaped | {core, mixer}    3 columns, none
     * </pre>
     *
     * It also silently disabled the layer spacing. With any compound in the graph, the gap between columns
     * came out at 20 units whatever the setting said — that is ELK's own default for
     * {@code spacing.nodeNodeBetweenLayers} — so a chart with a group came out far narrower than the same
     * chart without one. On a machine of a realistic size that is the difference between a wide chart and a
     * tall thin one.
     */
    @Test
    @DisplayName("a one-machine group keeps the chart left-to-right")
    public void aOneMachineGroupDoesNotCollapseAColumn() {
        // macerator -> shaped (alone in a group) -> {core, mixer}
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2), machine(3), machine(4)),
            List.of(relation(10, 1, 2), relation(11, 2, 3), relation(12, 2, 4)),
            List.of(group(90, 2)),
            List.of());

        final LayoutPlan plan = engine.layout(request, settings());

        for (final int[] pair : new int[][] { { 1, 2 }, { 2, 3 }, { 2, 4 } }) {
            final int from = plan.machines()
                .get(id(pair[0]))
                .leftInt();
            final int to = plan.machines()
                .get(id(pair[1]))
                .leftInt();
            assertTrue(
                from < to,
                "relation " + pair[0]
                    + " -> "
                    + pair[1]
                    + " does not run left to right ("
                    + from
                    + " -> "
                    + to
                    + "); a shared column means the arrow is drawn straight down");
        }

        // Three columns for a three-step chain: the producer, the grouped machine, the two consumers.
        assertEquals(3, columns(plan, 1, 2, 3, 4), "expected three distinct columns, got " + columns(plan, 1, 2, 3, 4));
    }

    @Test
    @DisplayName("a one-machine group still reports a frame that contains its member")
    public void aOneMachineGroupStillGetsAFrame() {
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2), machine(3)),
            List.of(relation(10, 1, 2), relation(11, 2, 3)),
            List.of(group(90, 2)),
            List.of());

        final LayoutPlan plan = engine.layout(request, settings());
        final Area frame = plan.groupFrames()
            .get(id(90));

        assertNotNull(frame, "a one-machine group lost its frame");
        assertTrue(frame.width >= 324 && frame.height >= 244, "the frame minimum was not applied: " + frame);

        final IntIntPair member = plan.machines()
            .get(id(2));
        assertTrue(
            frame.x <= member.leftInt() && member.leftInt() + 100 <= frame.ex(),
            "the member at " + member + " hangs out of " + frame);
        assertTrue(
            frame.y <= member.rightInt() && member.rightInt() + 80 <= frame.ey(),
            "the member at " + member + " hangs out of " + frame);

        // And nothing loose may end up inside it.
        for (final int loose : new int[] { 1, 3 }) {
            final IntIntPair at = plan.machines()
                .get(id(loose));
            assertFalse(box(at, 100, 80).intersects(frame), "loose machine " + loose + " landed inside the frame");
        }
    }

    @Test
    @DisplayName("two machines still make a real compound")
    public void aTwoMachineGroupIsStillACompound() {
        // The single-member rule must not swallow the case it was written for: two machines genuinely do
        // need laying out together inside the frame.
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2), machine(3), machine(4), machine(5)),
            List.of(relation(10, 1, 2), relation(11, 2, 3), relation(12, 4, 5), relation(13, 5, 3)),
            List.of(group(90, 4, 5)),
            List.of());

        final LayoutPlan plan = engine.layout(request, settings());
        final Area frame = plan.groupFrames()
            .get(id(90));

        assertNotNull(frame);
        for (final int member : new int[] { 4, 5 }) {
            final IntIntPair at = plan.machines()
                .get(id(member));
            assertTrue(
                frame.x <= at.leftInt() && at.leftInt() + 100 <= frame.ex(),
                "member " + member + " at " + at + " is outside " + frame);
        }
    }

    /** How many distinct columns the given machine seeds occupy. */
    private static int columns(final LayoutPlan plan, final int... seeds) {
        final Set<Integer> seen = new HashSet<>();
        for (final int seed : seeds) seen.add(
            plan.machines()
                .get(id(seed))
                .leftInt());
        return seen.size();
    }

    @Test
    @DisplayName("an empty group is placed, gets its minimum, and overlaps nothing")
    public void emptyGroupIsHandled() {
        // Reachable: drag the last machine out. It is laid out like everything else rather than left
        // where the author put it, on the reasoning that a staging area teleporting across the chart
        // every press is more surprising than one sitting apart. That only works because an empty
        // compound is otherwise 0x0 with its padding ignored, which this pins: without the filler the
        // frame is 324x244 of reserved nothing and the first machine is laid straight through it.
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2)),
            List.of(relation(10, 1, 2)),
            List.of(group(90)),
            List.of());

        final LayoutPlan plan = assertDoesNotThrow(() -> engine.layout(request, settings()));
        final Area frame = plan.groupFrames()
            .get(id(90));

        assertNotNull(frame, "an empty group was dropped");
        assertTrue(frame.width >= 324 && frame.height >= 244, "the minimum was not applied: " + frame);
        for (final IntIntPair at : plan.machines()
            .values()) {
            assertFalse(box(at, 100, 80).intersects(frame), "a machine landed inside " + frame);
        }
    }
}
