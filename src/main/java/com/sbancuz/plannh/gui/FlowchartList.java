package com.sbancuz.plannh.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Interpolation;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.ListWidget;

/**
 * A list whose sections can be reordered by dragging a {@link Grip} placed inside one of them.
 *
 * <p>
 * The list owns everything about a reorder: the live swap, the glide animation, and the change
 * callback. Children are never removed or re-added - MUI2 disposes widgets on removal and refuses
 * to re-add disposed ones - so a move sorts the children list in place.
 *
 * <p>
 * It also re-clamps the scroll offset after layout; ListWidget only does so on child churn, and a
 * resize that moves the visible size (the zoom-dependent max, folded sections) would otherwise
 * leave a bottom-anchored offset rendering past the panel.
 */
public final class FlowchartList extends ListWidget<IWidget, FlowchartList> {

    private static final int REORDER_ANIM_MS = 250;
    private static final long MOVE_COOLDOWN_MS = 120;

    /** Fired after every committed move with the children in their new order. */
    private Consumer<List<IWidget>> onMove;

    private boolean captureNextLayout = false;
    private int dragFrom = -1;
    private long lastMove = 0;

    private final List<Area> snapshots = new ArrayList<>();
    private final List<Glide> glides = new ArrayList<>();

    private record Glide(IWidget child, Area from, Area to, long start) {}

    public void beginReorderAnimation() {
        this.captureNextLayout = true;
    }

    public FlowchartList onMove(final Consumer<List<IWidget>> onMove) {
        this.onMove = onMove;
        return this;
    }

    private void moveTo(final int from, final int to) {
        if (from == to || from < 0 || to < 0 || from >= getChildren().size()) return;
        beginReorderAnimation();
        final List<IWidget> children = getTypeChildren();
        children.add(to, children.remove(from));
        if (dragFrom == from) dragFrom = to;
        lastMove = System.currentTimeMillis();
        scheduleResize();
        if (this.onMove != null) this.onMove.accept(List.copyOf(children));
    }

    private int childIndexOf(final IWidget widget) {
        IWidget current = widget;
        while (current.getParent() != this) {
            current = current.getParent();
        }
        return getChildren().indexOf(current);
    }

    private IWidget hoveredSibling() {
        final ModularPanel panel = getPanel();
        if (dragFrom < 0) return null;
        final IWidget dragged = getChildren().get(dragFrom);
        for (final LocatedWidget located : panel.getAllHoveringList(false)) {
            final IWidget target = resolveChild(located.getElement());
            if (target != null && target != dragged) return target;
        }
        return null;
    }

    private IWidget resolveChild(IWidget widget) {
        while (widget != null && widget.getParent() != this) {
            widget = widget.getParent();
        }
        return widget;
    }

    /**
     * This is to fix a bug where, when the scrollbar is at the bottom and you zoom out
     * the area becomes bigger than needed
     */
    @Override
    public boolean postLayoutWidgets() {
        final boolean done = super.postLayoutWidgets();
        getScrollData().clamp(getScrollArea());
        applyGlides();
        return done;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!this.glides.isEmpty()) scheduleResize();
    }

    private void applyGlides() {
        if (this.glides.isEmpty()) return;
        final long now = System.currentTimeMillis();
        this.glides.removeIf(glide -> {
            final float t = Math.min(1f, (now - glide.start()) / (float) REORDER_ANIM_MS);
            glide.child()
                .getArea()
                .interpolate(glide.from(), glide.to(), Interpolation.SINE_OUT.interpolate(0, 1, t));
            return t >= 1f;
        });
    }

    @Override
    public void beforeResize(final boolean onOpen) {
        super.beforeResize(onOpen);
        if (!this.captureNextLayout) return;
        this.snapshots.clear();
        for (final IWidget child : getChildren()) {
            this.snapshots.add(
                child.getArea()
                    .copyOrImmutable());
        }
    }

    @Override
    public void postResize() {
        super.postResize();
        if (!this.captureNextLayout) return;
        this.captureNextLayout = false;
        this.glides.clear();
        final long now = System.currentTimeMillis();
        final List<IWidget> children = getChildren();
        for (int i = 0; i < Math.min(children.size(), this.snapshots.size()); i++) {
            final Area area = children.get(i)
                .getArea();
            if (area.shouldAnimate(this.snapshots.get(i))) {
                // Layout already placed everything at its new spot; remember the pre-move snapshot
                // so the following layout passes can interpolate from it.
                this.glides.add(new Glide(children.get(i), this.snapshots.get(i), area.copyOrImmutable(), now));
            }
        }
    }

    public static final class Grip extends Widget<Grip> implements IDraggable {

        private static final String GRIP_TEXTURE = "textures/gui/summary_grip.png";
        private static final int WIDTH = 10;

        private final FlowchartList list;

        public Grip(FlowchartList sectionsList, final int accentColor, final int tintColor) {
            width(WIDTH).fullHeight()
                .background(
                    new Rectangle().color(accentColor),
                    new UITexture(new ResourceLocation("plannh", GRIP_TEXTURE), 0f, 0f, 1f, 1f, null, true, tintColor));
            list = sectionsList;
        }

        @Override
        public boolean onDragStart(final int mouseButton) {
            if (mouseButton != 0) return false;
            list.dragFrom = list.childIndexOf(this);
            return list.dragFrom >= 0;
        }

        @Override
        public void onDrag(final int mouseButton, final long timeSinceLastClick) {
            if (list.dragFrom < 0) return;
            final IWidget target = list.hoveredSibling();
            if (System.currentTimeMillis() - list.lastMove < MOVE_COOLDOWN_MS) return;

            if (target == null || target == list.getChildren()
                .get(list.dragFrom)) return;
            list.moveTo(
                list.dragFrom,
                list.getChildren()
                    .indexOf(target));
        }

        @Override
        public void onDragEnd(final boolean successful) {
            list.dragFrom = -1;
        }

        @Override
        public void drawMovingState(final ModularGuiContext context, final float partialTicks) {}

        @Override
        public void transform(final IViewportStack stack) {
            stack.translate(getArea().rx, getArea().ry, 0);
        }

        @Override
        public boolean isMoving() {
            return false;
        }

        @Override
        public void setMoving(final boolean moving) {}

        @Override
        public @Nullable Area getMovingArea() {
            return null;
        }
    }
}
