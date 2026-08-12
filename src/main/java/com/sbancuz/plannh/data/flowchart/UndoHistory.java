package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayDeque;
import java.util.Deque;

import com.sbancuz.plannh.data.serialization.Serializer;

/**
 * Stack-based undo/redo over whole-graph snapshots.
 *
 * <p>
 * Every user edit is bracketed by {@link #beginEdit} / {@link #commitEdit}: begin captures the
 * pre-edit state as a token the caller holds, commit records it only if the edit actually changed
 * something. Snapshots reuse the save-file encoding, so compound edits like cascading deletes need
 * no special handling.
 *
 * <p>
 * Camera state is normalized out of snapshots (and re-applied by the caller on restore), so
 * panning between edits neither pollutes the stack nor teleports the view on undo.
 */
public final class UndoHistory {

    private static final int MAX_DEPTH = 64;

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    /** Captures the state about to be edited; pass the token to {@link #commitEdit}. */
    public String beginEdit(final Graph graph) {
        return encodeNormalized(graph);
    }

    /** Records the pre-edit state; no-op edits leave no trace. */
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

    /** Camera fields come back at defaults. */
    public Graph undo(final Graph current) {
        redoStack.push(encodeNormalized(current));
        return Serializer.decodeGraph(undoStack.pop());
    }

    /** Camera fields come back at defaults. */
    public Graph redo(final Graph current) {
        undoStack.push(encodeNormalized(current));
        return Serializer.decodeGraph(redoStack.pop());
    }

    /**
     * Encodes with the camera zeroed so snapshots compare by content. The setters are plain
     * field writes, so the flip-encode-restore is invisible to everything else.
     */
    private static String encodeNormalized(final Graph graph) {
        final float zoom = graph.getZoom();
        final float panX = graph.getPanX();
        final float panY = graph.getPanY();
        graph.setZoom(1);
        graph.setPanX(0);
        graph.setPanY(0);
        try {
            return Serializer.encodeGraph(graph);
        } finally {
            graph.setZoom(zoom);
            graph.setPanX(panX);
            graph.setPanY(panY);
        }
    }
}
