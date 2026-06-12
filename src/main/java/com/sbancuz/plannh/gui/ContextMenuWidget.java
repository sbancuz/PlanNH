package com.sbancuz.plannh.gui;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

public class ContextMenuWidget extends Widget<ContextMenuWidget> implements Interactable {

    private static final int ITEM_H = 14;
    private static final int PAD = 3;
    private static final int MIN_WIDTH = 80;
    private static final int EXTRA_W = 4;
    private static final int BORDER_W = 1;
    private static final int TEXT_OFF = 2;

    public record MenuItem(String label, Runnable action) {}

    @Nonnull
    private final List<MenuItem> items;
    @Nonnull
    private final CanvasWidget canvas;

    public ContextMenuWidget(final CanvasWidget canvas, final List<MenuItem> items, final int x, final int y) {
        this.canvas = canvas;
        this.items = items;
        final Minecraft mc = Minecraft.getMinecraft();
        int maxW = MIN_WIDTH;
        for (final MenuItem item : items) {
            final int w = mc.fontRenderer.getStringWidth(item.label);
            if (w > maxW) maxW = w;
        }
        pos(x, y);
        size(maxW + PAD * 2 + EXTRA_W, items.size() * ITEM_H + PAD * 2);
    }

    @Override
    public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
        final int w = getArea().width;
        final int h = getArea().height;
        final int mx = getContext().getMouseX();
        final int my = getContext().getMouseY();

        GuiDraw.drawRect(0, 0, w, h, PlannhColors.CONTEXT_BG.getColor());
        GuiHelper.drawRectBorder(0, 0, w, h, BORDER_W, PlannhColors.CONTEXT_BORDER.getColor());

        for (int i = 0; i < items.size(); i++) {
            final int iy = PAD + i * ITEM_H;
            if (mx >= PAD && mx < w - PAD && my >= iy && my < iy + ITEM_H) {
                GuiDraw.drawRect(PAD, iy, w - PAD * 2, ITEM_H, PlannhColors.CONTEXT_HOVER.getColor());
            }
            GuiDraw.drawText(
                items.get(i).label,
                PAD + TEXT_OFF,
                iy + TEXT_OFF,
                1.0f,
                PlannhColors.TEXT_WHITE.getColor(),
                false);
        }
    }

    @Override
    public @Nonnull Result onMousePressed(final int mouseButton) {
        if (mouseButton == 0) {
            final int mx = getContext().getMouseX();
            final int my = getContext().getMouseY();
            for (int i = 0; i < items.size(); i++) {
                final int iy = PAD + i * ITEM_H;
                if (mx >= PAD && mx < getArea().width - PAD && my >= iy && my < iy + ITEM_H) {
                    items.get(i).action.run();
                    break;
                }
            }
            canvas.closeContextMenu();
            return Result.SUCCESS;
        }
        return Result.SUCCESS;
    }

    @Override
    public boolean onMouseScroll(final UpOrDown direction, final int amount) {
        return true;
    }
}
