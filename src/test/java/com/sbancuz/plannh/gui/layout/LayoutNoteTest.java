package com.sbancuz.plannh.gui.layout;

import static com.sbancuz.plannh.gui.layout.Charts.group;
import static com.sbancuz.plannh.gui.layout.Charts.id;
import static com.sbancuz.plannh.gui.layout.Charts.machine;
import static com.sbancuz.plannh.gui.layout.Charts.note;
import static com.sbancuz.plannh.gui.layout.Charts.relation;
import static com.sbancuz.plannh.gui.layout.Charts.settings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A note's space, and what a note does to the machine it belongs to.
 *
 * <p>
 * A note is not a layout node. It has no relations, so as a graph element it would land in layer 0 and
 * stretch every chart by a column — and ELK's own comment-box model places a comment above its node
 * unconditionally in a left-to-right layout, so it cannot honour a chosen side either. Its space comes
 * from growing the box of the machine it is anchored to, which is the one thing a node placer is
 * guaranteed to respect.
 */
public class LayoutNoteTest {

    private static final int GAP = 8;
    private static final int W = 100;
    private static final int H = 80;
    private static final int NOTE_W = 60;
    private static final int NOTE_H = 30;

    private final LayoutStrategy engine = new ElkLayoutStrategy();

    private static LayoutRequest withNote(final LayoutNote.Side side) {
        return new LayoutRequest(
            List.of(machine(1), machine(2)),
            List.of(relation(10, 1, 2)),
            List.of(),
            List.of(note(20, NOTE_W, NOTE_H, side, id(1))));
    }

    /**
     * The note sits exactly one gap from its machine's far edge, on the side it asked for.
     *
     * <p>
     * Asserting the gap exactly rather than "it does not overlap" is the point: it catches a missing gap
     * and a doubled one equally well, and a doubled one is invisible in a picture because the extra space
     * is empty.
     */
    @Test
    @DisplayName("the note sits exactly one gap from its machine, on the side it asked for")
    public void theNoteLandsExactlyOneGapAway() {
        for (final LayoutNote.Side side : LayoutNote.Side.values()) {
            final LayoutPlan plan = engine.layout(withNote(side), settings());
            final Point machineAt = plan.machines()
                .get(id(1));
            final Point noteAt = plan.notes()
                .get(id(20));

            assertNotNull(noteAt, "the note was not placed at all: " + side);
            switch (side) {
                case ABOVE -> assertEquals(machineAt.y() - NOTE_H - GAP, noteAt.y(), side + " gap");
                case BELOW -> assertEquals(machineAt.y() + H + GAP, noteAt.y(), side + " gap");
                case LEFT -> assertEquals(machineAt.x() - NOTE_W - GAP, noteAt.x(), side + " gap");
                case RIGHT -> assertEquals(machineAt.x() + W + GAP, noteAt.x(), side + " gap");
            }
            assertFalse(
                new Box(machineAt.x(), machineAt.y(), W, H).overlaps(new Box(noteAt.x(), noteAt.y(), NOTE_W, NOTE_H)),
                side + ": the note overlaps the machine it is attached to");
        }
    }

    @Test
    @DisplayName("a note is placed on the correct side of the column it belongs to")
    public void theNoteDoesNotCollideWithOtherColumns() {
        // The whole point of reserving space on the box: the machine beside the anchored one has to move
        // out of the way, or the note lands on top of it.
        final LayoutPlan plan = engine.layout(withNote(LayoutNote.Side.BELOW), settings());

        final Point machineAt = plan.machines()
            .get(id(1));
        final Point neighbourAt = plan.machines()
            .get(id(2));
        final Point noteAt = plan.notes()
            .get(id(20));

        assertFalse(
            new Box(neighbourAt.x(), neighbourAt.y(), W, H).overlaps(new Box(noteAt.x(), noteAt.y(), NOTE_W, NOTE_H)),
            "the note at " + noteAt + " landed on the neighbouring machine at " + neighbourAt);
        assertTrue(machineAt.x() < neighbourAt.x(), "the chain lost its direction");
    }

    @Test
    @DisplayName("several notes on one machine stack rather than pile up")
    public void severalNotesStack() {
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2)),
            List.of(relation(10, 1, 2)),
            List.of(),
            List.of(
                note(20, NOTE_W, NOTE_H, LayoutNote.Side.BELOW, id(1)),
                note(21, NOTE_W, NOTE_H, LayoutNote.Side.BELOW, id(1))));

        final LayoutPlan plan = engine.layout(request, settings());
        final Point first = plan.notes()
            .get(id(20));
        final Point second = plan.notes()
            .get(id(21));
        final Point machineAt = plan.machines()
            .get(id(1));

        assertNotNull(first);
        assertNotNull(second);
        assertFalse(
            new Box(first.x(), first.y(), NOTE_W, NOTE_H).overlaps(new Box(second.x(), second.y(), NOTE_W, NOTE_H)),
            "the two notes are on top of each other");
        assertTrue(second.y() >= first.y() + NOTE_H, "the second note is not stacked below the first");
        assertTrue(first.y() >= machineAt.y() + H, "the first note is not clear of the machine");
    }

    @Test
    @DisplayName("a note with no machine to anchor to reserves nothing and is simply absent")
    public void anUnanchorableNoteIsLeftAlone() {
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2)),
            List.of(relation(10, 1, 2)),
            List.of(),
            List.of(note(20, NOTE_W, NOTE_H, LayoutNote.Side.BELOW, null)));

        final LayoutPlan plan = engine.layout(request, settings());

        // The applier only writes coordinates for notes the plan mentions, so an absent note keeps the
        // position its author gave it. That needs no special case in the applier, which is why it is
        // worth asserting here.
        assertTrue(
            plan.notes()
                .isEmpty());
        assertEquals(
            2,
            plan.machines()
                .size());
    }

    @Test
    @DisplayName("a note inside a group is reserved inside that group")
    public void aGroupedNoteIsReserved() {
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2), machine(3)),
            List.of(relation(10, 1, 2), relation(11, 3, 2)),
            List.of(group(90, 2)),
            List.of(note(20, NOTE_W, NOTE_H, LayoutNote.Side.BELOW, id(2))));

        final LayoutPlan plan = engine.layout(request, settings());
        final Box frame = plan.groupFrames()
            .get(id(90));

        assertNotNull(frame);
        assertNotNull(
            plan.notes()
                .get(id(20)),
            "a grouped note was dropped");
        final Point machineAt = plan.machines()
            .get(id(2));
        final Point noteAt = plan.notes()
            .get(id(20));
        assertFalse(
            new Box(machineAt.x(), machineAt.y(), W, H).overlaps(new Box(noteAt.x(), noteAt.y(), NOTE_W, NOTE_H)),
            "the grouped note overlaps its machine");
        assertTrue(frame.y() <= machineAt.y(), "the grouped machine escaped its frame");
    }

    @Test
    @DisplayName("notes do not change the plan for machines they are not attached to")
    public void anUnrelatedNoteChangesNothing() {
        // A note whose anchor is absent must not perturb the machine layout at all - it reserves space
        // against nothing, so it is not in the request and the engine never hears about it.
        final LayoutRequest bare = new LayoutRequest(
            List.of(machine(1), machine(2)),
            List.of(relation(10, 1, 2)),
            List.of(),
            List.of());
        final LayoutRequest withDanglingNote = new LayoutRequest(
            List.of(machine(1), machine(2)),
            List.of(relation(10, 1, 2)),
            List.of(),
            List.of(note(20, NOTE_W, NOTE_H, LayoutNote.Side.BELOW, id(404))));

        final LayoutPlan barePlan = engine.layout(bare, settings());
        final LayoutPlan notedPlan = engine.layout(withDanglingNote, settings());

        assertEquals(barePlan.machines(), notedPlan.machines());
        assertTrue(
            notedPlan.notes()
                .isEmpty());
    }

    @Test
    @DisplayName("every note in a chart is placed")
    public void noNoteIsSilentlyDropped() {
        final List<LayoutNote> notes = List.of(
            note(20, NOTE_W, NOTE_H, LayoutNote.Side.ABOVE, id(1)),
            note(21, NOTE_W, NOTE_H, LayoutNote.Side.BELOW, id(2)),
            note(22, NOTE_W, NOTE_H, LayoutNote.Side.LEFT, id(3)));
        final LayoutRequest request = new LayoutRequest(
            List.of(machine(1), machine(2), machine(3)),
            List.of(relation(10, 1, 2), relation(11, 2, 3)),
            List.of(),
            notes);

        final Map<UUID, Point> placed = engine.layout(request, settings())
            .notes();

        for (final LayoutNote note : notes) {
            assertTrue(placed.containsKey(note.id()), "note " + note.id() + " was dropped");
        }
    }
}
