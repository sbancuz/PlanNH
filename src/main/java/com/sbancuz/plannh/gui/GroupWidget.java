package com.sbancuz.plannh.gui;

import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ColorPickerDialog;
import com.sbancuz.plannh.data.flowchart.Group;

import lombok.Getter;

public class GroupWidget extends Widget<GroupWidget> implements Interactable {

    private static final int HEADER_H = 20;
    private static final int CLOSE_W = 12;
    private static final int COLLAPSE_W = 14;
    private static final int SWATCH_W = 10;
    private static final int MIN_W = 180;
    private static final int MIN_H = 60;
    private static final int RESIZE_MARGIN = 3;
    private static final int ALPHA_BORDER = 120;
    private static final int ALPHA_HEADER = 180;

    private enum ResizeMode {

        NONE(0, 0, ""),
        TL(-1, -1, "\u2196"),
        T(0, -1, "\u2195"),
        TR(1, -1, "\u2197"),
        R(1, 0, "\u2194"),
        BR(1, 1, "\u2198"),
        B(0, 1, "\u2195"),
        BL(-1, 1, "\u2199"),
        L(-1, 0, "\u2194");

        final int hDir;
        final int vDir;
        final String cursor;

        ResizeMode(final int hDir, final int vDir, final String cursor) {
            this.hDir = hDir;
            this.vDir = vDir;
            this.cursor = cursor;
        }
    }

    @Getter
    private final Group group;
    private final CanvasWidget canvas;

    private boolean dragging = false;
    private int dragStartMX, dragStartMY;
    private int groupStartX, groupStartY;

    private ResizeMode resizeMode = ResizeMode.NONE;
    private int resizeStartMX, resizeStartMY;
    private int resizeStartX, resizeStartY;
    private int resizeStartW, resizeStartH;

    @Getter
    private boolean editing = false;
    @Getter
    private int cursorPos;
    private final GuiHelper.DoubleClickDetector doubleClick = new GuiHelper.DoubleClickDetector();

    public GroupWidget(final Group group, final CanvasWidget canvas) {
        this.group = group;
        this.canvas = canvas;
        final float z = canvas.getZoom();
        size(Math.round(group.width * z), computePixelHeight(z));
    }

    public void setEditing(final boolean editing) {
        this.editing = editing;
        this.cursorPos = group.title.length();
        if (!editing) {
            canvas.onGroupEditingDone();
        }
    }

    public void incrementCursor() {
        if (cursorPos < group.title.length()) cursorPos++;
    }

    public void decrementCursor() {
        if (cursorPos > 0) cursorPos--;
    }

    public void syncTransform(final float zoom, final float panX, final float panY) {
        final int sx = Math.round(group.x * zoom + panX);
        final int sy = Math.round(group.y * zoom + panY);
        pos(sx, sy);
        size(Math.round(group.width * zoom), computePixelHeight(zoom));
    }

    private int zq(final float v) {
        return GuiHelper.zq(v, canvas.getZoom());
    }

    private int computePixelHeight(final float z) {
        if (group.collapsed) return Math.round(HEADER_H * z);
        return Math.round(Math.max(group.height, MIN_H) * z);
    }

    private int displayColor() {
        return group.colorOverride != 0 ? group.colorOverride : PlannhColors.titleColor(group.title);
    }

    @Override
    public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
        final float z = canvas.getZoom();
        final int pw = getArea().width;
        final int ph = getArea().height;
        final int hh = Math.round(HEADER_H * z);
        final int titleCol = displayColor();

        GuiDraw.drawRect(0, 0, pw, ph, PlannhColors.SUMMARY_BG.getColor());

        final int bw = Math.max(1, Math.round(2 * z));
        final int bc = Color
            .argb(Color.getRed(titleCol), Color.getGreen(titleCol), Color.getBlue(titleCol), ALPHA_BORDER);
        GuiHelper.drawRectBorder(0, 0, pw, ph, bw, bc);

        final int hbg = Color
            .argb(Color.getRed(titleCol), Color.getGreen(titleCol), Color.getBlue(titleCol), ALPHA_HEADER);
        GuiDraw.drawRect(0, 0, pw, hh, hbg);
        GuiDraw.drawRect(0, hh, pw, Math.max(1, Math.round(z)), bc);

        final String collapseIcon = group.collapsed ? "\u25b6" : "\u25bc";
        GuiDraw.drawText(collapseIcon, zq(4), zq(4), z, PlannhColors.TEXT_LIGHT.getColor(), false);

        drawTitle(pw, hh, titleCol, z);

        final int ss = zq(SWATCH_W);
        final int sh = Math.round(10 * z);
        final int sx = pw - zq(CLOSE_W) - ss - zq(4) - Math.max(1, Math.round(z));
        GuiDraw.drawRect(sx, zq(4), ss, sh, titleCol);

        GuiHelper.drawCloseButton(
            z,
            pw,
            CLOSE_W,
            Math.max(1, Math.round(z)),
            PlannhColors.NOTE_CLOSE_BG.getColor(),
            PlannhColors.TEXT_WHITE.getColor());

        if (!group.collapsed) {
            final int lmx = getContext().getAbsMouseX() - getArea().x;
            final int lmy = getContext().getAbsMouseY() - getArea().y;
            final ResizeMode rm = getResizeModeAt(lmx, lmy);
            if (rm != ResizeMode.NONE && !rm.cursor.isEmpty()) {
                GuiDraw.drawText(rm.cursor, lmx + zq(10), lmy + zq(10), z * 1.2f, titleCol, false);
            }
        }
    }

    private void drawTitle(final int pw, final int hh, final int titleCol, final float z) {
        if (group.collapsed) return;

        final int titleX = Math.round((COLLAPSE_W + 4) * z);
        final int swatchW = Math.round(SWATCH_W * z);
        final int maxTitleW = pw - titleX - swatchW - zq(4) - Math.round((CLOSE_W + 6) * z);

        if (editing) {
            final String display = group.title.substring(0, Math.min(cursorPos, group.title.length()));
            final boolean blink = (Minecraft.getSystemTime() / 600) % 2 == 0;
            final String cursor = blink ? "|" : " ";
            String editText = display + cursor + group.title.substring(Math.min(cursorPos, group.title.length()));

            if (Math.round(Minecraft.getMinecraft().fontRenderer.getStringWidth(editText) * z) > maxTitleW) {
                final int maxChars = Math
                    .max(1, (int) (maxTitleW / (Minecraft.getMinecraft().fontRenderer.getStringWidth("W") * z)));
                if (editText.length() > maxChars) {
                    editText = editText.substring(editText.length() - maxChars);
                }
            }
            GuiDraw.drawText(editText, titleX, zq(4), z, PlannhColors.TEXT_WHITE.getColor(), false);
        } else {
            String displayTitle = group.title;
            final int titleW = Minecraft.getMinecraft().fontRenderer.getStringWidth(displayTitle);
            if (Math.round(titleW * z) > maxTitleW) {
                while (!displayTitle.isEmpty()
                    && Math.round(Minecraft.getMinecraft().fontRenderer.getStringWidth(displayTitle) * z) > maxTitleW) {
                    displayTitle = displayTitle.substring(0, displayTitle.length() - 1);
                }
                displayTitle += "\u2026";
            }
            GuiDraw.drawText(displayTitle, titleX, zq(4), z, PlannhColors.TEXT_WHITE.getColor(), false);

            final int badgeX = titleX
                + Math.round(Minecraft.getMinecraft().fontRenderer.getStringWidth(displayTitle) * z)
                + zq(6);
            GuiDraw.drawText(
                "(" + group.nodeIds.size() + ")",
                badgeX,
                zq(4),
                z * 0.8f,
                PlannhColors.TEXT_MUTED.getColor(),
                false);
        }
    }

    @Override
    public @NotNull Result onMousePressed(final int mouseButton) {
        if (mouseButton != 0) return Result.IGNORE;
        final float z = canvas.getZoom();
        final int mx = getContext().getMouseX();
        final int my = getContext().getMouseY();
        final int pw = getArea().width;
        final int hh = Math.round(HEADER_H * z);

        if (GuiHelper.isInsideCloseButton(mx, my, z, pw, CLOSE_W, Math.max(1, Math.round(z)))) {
            canvas.removeGroup(group.id);
            return Result.SUCCESS;
        }

        final int ss = zq(SWATCH_W);
        final int sx = pw - zq(CLOSE_W) - ss - zq(4) - Math.max(1, Math.round(z));
        final int sh = Math.round(10 * z);
        if (mx >= sx && mx < sx + ss && my >= zq(4) && my < zq(4) + sh) {
            openColorPicker();
            return Result.SUCCESS;
        }

        if (my < hh && mx < Math.round(COLLAPSE_W * z)) {
            group.collapsed = !group.collapsed;
            size(Math.round(group.width * z), computePixelHeight(z));
            canvas.setGroupNodesVisible(group.id, !group.collapsed);
            return Result.SUCCESS;
        }

        if (!group.collapsed) {
            final ResizeMode rm = getResizeModeAt(mx, my);
            if (rm != ResizeMode.NONE) {
                startResize(rm);
                return Result.SUCCESS;
            }
        }

        if (my < hh) {
            if (doubleClick.check()) {
                canvas.startEditingGroup(group.id);
                return Result.SUCCESS;
            }
            startDrag();
            return Result.SUCCESS;
        }

        return Result.IGNORE;
    }

    private void openColorPicker() {
        final int currentColor = group.colorOverride != 0 ? group.colorOverride : displayColor();
        final ColorPickerDialog picker = new ColorPickerDialog(chosenColor -> {
            group.colorOverride = chosenColor;
            canvas.rebuildGroupWidgets();
        }, currentColor, false);
        IPanelHandler.simple(getPanel(), (parent, player) -> picker, true)
            .openPanel();
    }

    private void startResize(final ResizeMode rm) {
        resizeMode = rm;
        resizeStartMX = getContext().getAbsMouseX();
        resizeStartMY = getContext().getAbsMouseY();
        resizeStartX = group.x;
        resizeStartY = group.y;
        resizeStartW = group.width;
        resizeStartH = group.height;
    }

    private void startDrag() {
        dragging = true;
        dragStartMX = getContext().getAbsMouseX();
        dragStartMY = getContext().getAbsMouseY();
        groupStartX = group.x;
        groupStartY = group.y;
    }

    private ResizeMode getResizeModeAt(final int mx, final int my) {
        final float z = canvas.getZoom();
        final int rm = Math.max(1, Math.round(RESIZE_MARGIN * z));
        final int pw = getArea().width;
        final int ph = getArea().height;

        final boolean top = my < rm;
        final boolean bottom = my > ph - rm;
        final boolean left = mx < rm;
        final boolean right = mx > pw - rm;

        if (top && left) return ResizeMode.TL;
        if (top && right) return ResizeMode.TR;
        if (bottom && left) return ResizeMode.BL;
        if (bottom && right) return ResizeMode.BR;
        if (top) return ResizeMode.T;
        if (bottom) return ResizeMode.B;
        if (left) return ResizeMode.L;
        if (right) return ResizeMode.R;
        return ResizeMode.NONE;
    }

    @Override
    public boolean onMouseRelease(final int mouseButton) {
        if (resizeMode != ResizeMode.NONE) {
            resizeMode = ResizeMode.NONE;
            canvas.recheckMembershipAndFit();
            return true;
        }
        if (dragging) {
            dragging = false;
            canvas.recheckMembershipAndFit();
        }
        return true;
    }

    @Override
    public void onMouseDrag(final int mouseButton, final long timeSinceClick) {
        if (resizeMode != ResizeMode.NONE && mouseButton == 0) {
            handleResizeDrag();
        } else if (dragging && mouseButton == 0) {
            handlePositionDrag();
        }
    }

    private void handleResizeDrag() {
        final int dx = getContext().getAbsMouseX() - resizeStartMX;
        final int dy = getContext().getAbsMouseY() - resizeStartMY;
        final float z = canvas.getZoom();
        final int dgx = Math.round(dx / z);
        final int dgy = Math.round(dy / z);

        int newX = resizeStartX, newY = resizeStartY;
        int newW = resizeStartW, newH = resizeStartH;
        final int hDir = resizeMode.hDir;
        final int vDir = resizeMode.vDir;

        if (hDir < 0) {
            newX = resizeStartX + dgx;
            newW = resizeStartW - dgx;
        } else if (hDir > 0) {
            newW = resizeStartW + dgx;
        }

        if (vDir < 0) {
            newY = resizeStartY + dgy;
            newH = resizeStartH - dgy;
        } else if (vDir > 0) {
            newH = resizeStartH + dgy;
        }

        if (newW < MIN_W) {
            if (hDir < 0) newX = resizeStartX + resizeStartW - MIN_W;
            newW = MIN_W;
        }
        if (newH < MIN_H) {
            if (vDir < 0) newY = resizeStartY + resizeStartH - MIN_H;
            newH = MIN_H;
        }

        group.x = newX;
        group.y = newY;
        group.width = newW;
        group.height = newH;
        syncTransform(z, canvas.getPanX(), canvas.getPanY());
    }

    private void handlePositionDrag() {
        final int dx = getContext().getAbsMouseX() - dragStartMX;
        final int dy = getContext().getAbsMouseY() - dragStartMY;
        final float z = canvas.getZoom();
        final int newX = groupStartX + Math.round(dx / z);
        final int newY = groupStartY + Math.round(dy / z);
        final int deltaX = newX - group.x;
        final int deltaY = newY - group.y;
        group.x = newX;
        group.y = newY;
        canvas.moveGroupNodes(group.id, deltaX, deltaY);
        syncTransform(z, canvas.getPanX(), canvas.getPanY());
    }
}
