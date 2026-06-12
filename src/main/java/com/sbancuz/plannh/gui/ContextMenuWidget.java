package com.sbancuz.plannh.gui;

import java.util.List;

import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

public class ContextMenuWidget extends Widget<ContextMenuWidget> implements Interactable {

    private static final int ITEM_H = 14;
    private static final int PAD = 3;

    public record MenuItem(String label, Runnable action) {}

    private final List<MenuItem> items;
    private final CanvasWidget canvas;

    public ContextMenuWidget(CanvasWidget canvas, List<MenuItem> items, int x, int y) {
        this.canvas = canvas;
        this.items = items;
        Minecraft mc = Minecraft.getMinecraft();
        int maxW = 80;
        for (MenuItem item : items) {
            int w = mc.fontRenderer.getStringWidth(item.label);
            if (w > maxW) maxW = w;
        }
        pos(x, y);
        size(maxW + PAD * 2 + 4, items.size() * ITEM_H + PAD * 2);
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        int w = getArea().width;
        int h = getArea().height;
        int mx = getContext().getMouseX();
        int my = getContext().getMouseY();

        GuiDraw.drawRect(0, 0, w, h, PlannhColors.CONTEXT_BG.getColor());
        GuiHelper.drawRectBorder(0, 0, w, h, 1, PlannhColors.CONTEXT_BORDER.getColor());

        for (int i = 0; i < items.size(); i++) {
            int iy = PAD + i * ITEM_H;
            if (mx >= PAD && mx < w - PAD && my >= iy && my < iy + ITEM_H) {
                GuiDraw.drawRect(PAD, iy, w - PAD * 2, ITEM_H, PlannhColors.CONTEXT_HOVER.getColor());
            }
            GuiDraw.drawText(items.get(i).label, PAD + 2, iy + 2, 1.0f, PlannhColors.TEXT_WHITE.getColor(), false);
        }
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (mouseButton == 0) {
            int mx = getContext().getMouseX();
            int my = getContext().getMouseY();
            for (int i = 0; i < items.size(); i++) {
                int iy = PAD + i * ITEM_H;
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
    public boolean onMouseScroll(UpOrDown direction, int amount) {
        return true;
    }
}
