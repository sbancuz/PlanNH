package com.sbancuz.plannh.gui.node;

import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.ClampedListWidget;

abstract class NodeFoldList<W extends NodeFoldList<W>> extends ClampedListWidget<IWidget, W> {

    private static final int SCROLLBAR_GAP = 4;

    /** The gap between two rows of a list, and either side of the rule between two lists. */
    protected static final int ROW_GAP = 4;

    private static final BooleanSupplier ALWAYS = () -> true;

    protected final NodeWidget node;

    /** What the rows were built from: a change in it, or in the recipe, is a rebuild. */
    private Object builtWith;
    private int tallestRow;
    private boolean dirty = true;

    /**
     * @param open what decides whether the fold is showing, or null for one that always is.
     */
    protected NodeFoldList(final NodeWidget node, final int visibleRows, final @Nullable BooleanSupplier open) {
        this.node = node;
        this.builtWith = node.getData()
            .getExtractor();

        showScrollShadows(false);
        final BooleanSupplier enabled = (open == null ? ALWAYS : open);

        fullWidth().crossAxisAlignment(Alignment.CrossAxis.START)
            .paddingRight(SCROLLBAR_GAP)
            .scrollDirection(new VerticalScrollData())
            .setEnabledIf(_ -> enabled.getAsBoolean())
            .maxSize(() -> tallestRow == 0 ? Integer.MAX_VALUE : visibleRows * tallestRow);

        onUpdateListener(w -> {
            if (dirty || builtWith != node.getData()
                .getExtractor()) {

                if (holdsFocus()) return;
                dirty = false;
                builtWith = node.getData()
                    .getExtractor();

                rebuild();
            }
        }, true);
    }

    /** The rows, from whatever they are a function of. Called on construction and on every rebuild. */
    protected abstract void rebuild();

    @Override
    public boolean postLayoutWidgets() {
        final boolean done = super.postLayoutWidgets();
        int tallest = 0;
        for (final IWidget child : getChildren()) {
            tallest = Math.max(
                tallest,
                child.getArea()
                    .getSize(GuiAxis.Y)
                    + child.getArea()
                        .getMargin()
                        .getTotal(GuiAxis.Y));
        }
        if (tallest != tallestRow) {
            tallestRow = tallest;
            scheduleResize();
        }
        return done;
    }

    @Override
    public boolean canClickThrough() {
        return true;
    }

    private boolean holdsFocus() {
        final LocatedWidget focused = getContext().getFocusedWidget();
        for (IWidget w = focused == null ? null : focused.getElement(); w != null; w = w.getParent()) {
            if (w == this) return true;
        }
        return false;
    }

    protected void commitEdit(final Runnable change) {
        final CanvasWidget canvas = node.getCanvas();
        PlanAPI.recordEdit(canvas.getGraph(), change);
        canvas.getGraph()
            .bumpVersion();
        PlanAPI.save();
    }

    /** Asks for a rebuild on the next update that finds no focused field. */
    protected void markStale() {
        dirty = true;
    }
}
