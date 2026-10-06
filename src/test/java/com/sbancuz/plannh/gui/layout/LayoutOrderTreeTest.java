package com.sbancuz.plannh.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the ordering actually has to guarantee.
 *
 * <p>
 * Almost all of what an earlier attempt tested here was Tarjan, condensation heights and
 * Weisfeiler-Leman refinement — code that is now behind a sweep rather than shipped. What is left is
 * the part that is a <em>requirement</em> rather than a heuristic: a total order that does not depend on
 * the order the builder happened to walk the chart in, and a tree shape that keeps a group's members
 * together.
 */
public class LayoutOrderTreeTest {

    /** A machine with a box and no pins; the ordering does not look at geometry. */
    private static LayoutMachine machine(final int seed) {
        return new LayoutMachine(id(seed), "m" + seed, 100, 80);
    }

    private static LayoutGroup group(final int seed, final int pad, final int header, final Integer... members) {
        final List<UUID> ids = new ArrayList<>();
        for (final Integer member : members) ids.add(id(member));
        return new LayoutGroup(id(seed), ids, pad, header);
    }

    private static UUID id(final int seed) {
        return new UUID(0, seed);
    }

    @Test
    @DisplayName("a group's members come out as one consecutive run")
    public void groupMembersAreContiguous() {
        // Loose machines interleaved between a group's members would be laid out inside the group's
        // compound, which is wrong rather than untidy.
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2), machine(3), machine(4), machine(5)),
            List.of(),
            List.of(group(90, 12, 20, 2, 4)),
            List.of());

        final List<UUID> machines = new CanonicalOrder(request).machines();

        assertEquals(5, machines.size());
        final int first = machines.indexOf(id(2));
        final int second = machines.indexOf(id(4));
        assertEquals(1, second - first, "the group's two machines must be adjacent: " + machines);
    }

    @Test
    @DisplayName("a nested group is emitted after its parent and before its parent's other members")
    public void nestingIsDepthFirst() {
        // Inner group (91) holds machine 4; outer group (90) holds machines 2, 3 and the inner group.
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2), machine(3), machine(4), machine(5)),
            List.of(),
            List.of(group(90, 12, 20, 2, 3, 91), group(91, 8, 18, 4)),
            List.of());

        final CanonicalOrder order = new CanonicalOrder(request);

        assertEquals(List.of(id(90), id(91)), order.groupIds(), "a parent must precede what is inside it");
        assertEquals(id(90), order.parentOf(id(91)));
        assertEquals(id(91), order.parentOf(id(4)));
        assertNull(order.parentOf(id(1)), "a loose machine has no parent group");
        assertNull(order.parentOf(id(90)));
    }

    @Test
    @DisplayName("the order is the same however the builder walked the chart")
    public void orderIsIndependentOfInputOrder() {
        // The guarantee the whole class exists for. Shuffling the request must not change the order.
        final List<LayoutMachine> machines = new ArrayList<>(
            List.of(machine(7), machine(3), machine(9), machine(1), machine(8), machine(2)));
        final List<LayoutGroup> groups = List.of(group(90, 12, 20, 3, 9), group(91, 8, 18, 8));

        final List<UUID> reference = new CanonicalOrder(
            new LayoutRequest(List.copyOf(machines), List.of(), groups, List.of())).machines();

        for (int shuffle = 0; shuffle < 20; shuffle++) {
            final List<LayoutMachine> shuffled = new ArrayList<>(machines);
            java.util.Collections.shuffle(shuffled, new java.util.Random(shuffle));
            final List<UUID> actual = new CanonicalOrder(new LayoutRequest(shuffled, List.of(), groups, List.of()))
                .machines();
            assertEquals(reference, actual, "shuffle " + shuffle + " produced a different order");
        }
    }

    @Test
    @DisplayName("a group keys on its contents, so it is seeded where its machines would have been")
    public void groupKeysOnEarliestMember() {
        // The group takes the earliest member id, so it lands in the same part of the order its
        // contents would have. An empty group falls back to its own id.
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(5), machine(6), machine(7)),
            List.of(),
            List.of(group(90, 12, 20, 6), group(95, 12, 20)),
            List.of());

        final List<UUID> machines = new CanonicalOrder(request).machines();

        // Group 90 keys on 6, group 95 (empty) keys on 95, machine 5 keys on 5: 5, 90, 95.
        assertEquals(List.of(id(5), id(6), id(7)), machines);
        assertTrue(machines.indexOf(id(6)) < machines.indexOf(id(7)));
    }

    @Test
    @DisplayName("a group holding itself terminates instead of recursing forever")
    public void corruptNestingTerminates() {
        // Unreachable from a chart built by dragging, which is exactly why it needs a guard rather
        // than a check. Without one this is a StackOverflowError inside a click handler.
        final LayoutGroup self = new LayoutGroup(id(90), List.of(id(90)), 12, 20);
        final LayoutRequest request = new LayoutRequest(List.of(), List.of(), List.of(self), List.of());

        assertEquals(List.of(id(90)), new CanonicalOrder(request).groupIds());
    }

    @Test
    @DisplayName("a relation to a machine outside the request is dropped, not crashed")
    public void relationsAreFilteredToPresentMachines() {
        // Mandatory, not defensive: an ElkEdge with a missing endpoint is a hard NPE inside ELK's
        // importer rather than a graceful skip.
        final LayoutRelation present = new LayoutRelation(id(50), id(1), id(2));
        final LayoutRelation dangling = new LayoutRelation(id(51), id(1), id(404));
        final LayoutRelation selfLoop = new LayoutRelation(id(52), id(1), id(1));

        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2)),
            List.of(dangling, present, selfLoop),
            List.of(),
            List.of());

        final List<LayoutRelation> relations = new CanonicalOrder(request).relations();

        assertEquals(2, relations.size());
        assertFalse(relations.contains(dangling));
        assertTrue(relations.contains(present));
        assertTrue(
            relations.contains(selfLoop),
            "self-loops and parallel relations are kept: crossing counts depend on multiplicity");
    }

    @Test
    @DisplayName("an empty request orders to nothing")
    public void emptyRequestIsEmpty() {
        final CanonicalOrder order = new CanonicalOrder(new LayoutRequest(List.of(), List.of(), List.of(), List.of()));

        assertTrue(
            order.machines()
                .isEmpty());
        assertTrue(
            order.groupIds()
                .isEmpty());
        assertTrue(
            order.relations()
                .isEmpty());
    }

    /**
     * The seam, enforced mechanically rather than by a promise in a javadoc: nothing in the package
     * but the engine may name an engine type.
     *
     * <p>
     * The interface's contract is what it does not mention. That is easy to state and easy to erode —
     * one convenience import of {@code ElkNode} in a DTO and the whole point of the seam is gone, with
     * no compiler involved either way.
     */
    @Test
    @DisplayName("no file but the strategy imports an engine type")
    public void seamHolds() throws IOException {
        final Path sources = Path.of("src", "main", "java", "com", "sbancuz", "plannh", "gui", "layout");
        assertTrue(Files.isDirectory(sources), "cannot find the layout sources at " + sources.toAbsolutePath());

        // Exactly one file may name the engine, and it is the one that implements it.
        final List<String> allowed = List.of("ElkLayoutStrategy.java");

        final List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.list(sources)) {
            for (final Path file : files.filter(
                path -> path.toString()
                    .endsWith(".java"))
                .toList()) {
                final String name = file.getFileName()
                    .toString();
                if (!allowed.contains(name) && Files.readString(file)
                    .contains("org.eclipse.elk")) {
                    offenders.add(name);
                }
            }
        } catch (final UncheckedIOException e) {
            throw e.getCause();
        }

        assertTrue(offenders.isEmpty(), "these files reach into the layout engine: " + offenders);
        assertNotNull(LayoutStrategy.class, "the seam itself must exist");
        assertEquals(
            1,
            LayoutStrategy.class.getDeclaredMethods().length,
            "LayoutStrategy should carry exactly one method: layout(). A second one is an extension point"
                + " nothing has ever needed");
    }
}
