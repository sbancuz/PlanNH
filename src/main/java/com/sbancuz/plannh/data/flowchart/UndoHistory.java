package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Stack-based undo/redo over whole-graph snapshots.
 *
 * <p>
 * Every user edit is bracketed by {@link #beginEdit} / {@link #commitEdit}: begin captures the
 * pre-edit state as a token the caller holds, commit records it only if the edit actually
 * changed something. Callers own their tokens, so overlapping brackets (a text field losing
 * focus while a drag starts) cannot corrupt each other. Snapshots reuse the save-file encoding,
 * so one mechanism covers every operation - node, edge, note, group and machine-config alike -
 * including compound ones like cascading deletes, without per-operation command classes.
 *
 * <p>
 * Camera state is normalized out of snapshots (and re-applied by the caller on restore), so
 * panning between edits neither pollutes the stack nor teleports the view on undo.
 */
public final class UndoHistory {

    /** Snapshots beyond this are dropped oldest-first; ~KBs each, so memory stays bounded. */
    private static final int MAX_DEPTH = 64;

    /** Pre-edit states, most recent first. */
    private final Deque<String> undoStack = new ArrayDeque<>();
    /** States undone from, most recent first; cleared by any new edit. */
    private final Deque<String> redoStack = new ArrayDeque<>();

    /** Captures the state about to be edited; pass the token to {@link #commitEdit}. */
    public String beginEdit(final Graph graph) {
        return encodeNormalized(graph);
    }

    /** Records the pre-edit state if the edit changed the graph; no-op edits leave no trace. */
    public void commitEdit(final String before, final Graph graph) {
        if (before == null || before.equals(encodeNormalized(graph))) return;
        undoStack.push(before);
        if (undoStack.size() > MAX_DEPTH) undoStack.removeLast();
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** Returns the previous state; the caller swaps it in. Camera fields come back at defaults. */
    public Graph undo(final Graph current) {
        redoStack.push(encodeNormalized(current));
        return Serializer.decode(undoStack.pop());
    }

    /** Returns the next state; the caller swaps it in. Camera fields come back at defaults. */
    public Graph redo(final Graph current) {
        undoStack.push(encodeNormalized(current));
        return Serializer.decode(redoStack.pop());
    }

    /** Drops all history; used when the active graph is replaced wholesale (slot switch etc.). */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    /**
     * Encodes with the camera zeroed so snapshots compare by content. The setters are plain
     * field writes, so the flip-encode-restore is invisible to everything else.
     */
    private static String encodeNormalized(final Graph graph) {
        final float zoom = graph.getZoom();
        final float panX = graph.getPanX();
        final float panY = graph.getPanY();
        graph.setZoom(1f);
        graph.setPanX(0f);
        graph.setPanY(0f);
        try {
            return Serializer.encode(graph);
        } finally {
            graph.setZoom(zoom);
            graph.setPanX(panX);
            graph.setPanY(panY);
        }
    }
}
