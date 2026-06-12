package com.sbancuz.plannh.gui;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ColorPickerDialog;
import com.sbancuz.plannh.data.flowchart.Group;

public class GroupWidget extends Widget<GroupWidget> implements Interactable {

    private static final int HEADER_H = 20;
    private static final int CLOSE_W = 12;
    private static final int COLLAPSE_W = 14;
    private static final int SWATCH_W = 10;
    private static final int MIN_W = 180;
    private static final int MIN_H = 60;
    private static final int RESIZE_MARGIN = 3;

    private enum ResizeMode {
        NONE,
        TL,
        T,
        TR,
        R,
        BR,
        B,
        BL,
        L
    }

    private final Group group;
    private final CanvasWidget canvas;

    private boolean dragging = false;
    private int dragStartMX, dragStartMY;
    private int groupStartX, groupStartY;

    private ResizeMode resizeMode = ResizeMode.NONE;
    private int resizeStartMX, resizeStartMY;
    private int resizeStartX, resizeStartY;
    private int resizeStartW, resizeStartH;

    private boolean editing = false;
    private int cursorPos;
    private long lastClickTime;

    public GroupWidget(Group group, CanvasWidget canvas) {
        this.group = group;
        this.canvas = canvas;
        float z = canvas.getZoom();
        size(Math.round(group.width * z), computePixelHeight(z));
    }

    public Group getGroup() {
        return group;
    }

    public boolean isEditing() {
        return editing;
    }

    public void setEditing(boolean editing) {
        this.editing = editing;
        this.cursorPos = group.title.length();
        if (!editing) {
            canvas.onGroupEditingDone();
        }
    }

    public int getCursorPos() {
        return cursorPos;
    }

    public void incrementCursor() {
        if (cursorPos < group.title.length()) cursorPos++;
    }

    public void decrementCursor() {
        if (cursorPos > 0) cursorPos--;
    }

    public void syncTransform(float zoom, float panX, float panY) {
        int sx = Math.round(group.x * zoom + panX);
        int sy = Math.round(group.y * zoom + panY);
        pos(sx, sy);
        size(Math.round(group.width * zoom), computePixelHeight(zoom));
    }

    private int computePixelHeight(float z) {
        if (group.collapsed) return Math.round(HEADER_H * z);
        return Math.round(Math.max(group.height, MIN_H) * z);
    }

    private int titleColor() {
        return group.colorOverride != 0 ? group.colorOverride : PlannhColors.titleColor(group.title);
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        float z = canvas.getZoom();
        int pw = getArea().width;
        int ph = getArea().height;
        int hh = Math.round(HEADER_H * z);

        int titleCol = titleColor();
        int borderCol = Color.argb(Color.getRed(titleCol), Color.getGreen(titleCol), Color.getBlue(titleCol), 120);
        int headerBg = Color.argb(Color.getRed(titleCol), Color.getGreen(titleCol), Color.getBlue(titleCol), 180);

        // Background
        GuiDraw.drawRect(0, 0, pw, ph, PlannhColors.SUMMARY_BG.getColor());

        // Border
        int bw = Math.max(1, Math.round(2 * z));
        GuiDraw.drawRect(0, 0, pw, bw, borderCol);
        GuiDraw.drawRect(0, ph - bw, pw, bw, borderCol);
        GuiDraw.drawRect(0, 0, bw, ph, borderCol);
        GuiDraw.drawRect(pw - bw, 0, bw, ph, borderCol);

        // Header bar
        GuiDraw.drawRect(0, 0, pw, hh, headerBg);

        // Separator line under header
        GuiDraw.drawRect(0, hh, pw, Math.max(1, Math.round(z)), borderCol);

        // Collapse toggle
        String collapseIcon = group.collapsed ? "\u25b6" : "\u25bc";
        int collapseX = Math.round(4 * z);
        GuiDraw.drawText(collapseIcon, collapseX, Math.round(4 * z), z, PlannhColors.TEXT_LIGHT.getColor(), false);

        // Title (or editing display)
        int titleX = Math.round((COLLAPSE_W + 4) * z);
        int swatchW = Math.round(SWATCH_W * z);
        int swatchGap = Math.round(4 * z);
        int maxTitleW = pw - titleX - swatchW - swatchGap - Math.round((CLOSE_W + 6) * z);

        if (editing) {
            String display = group.title.substring(0, Math.min(cursorPos, group.title.length()));
            boolean blink = (System.currentTimeMillis() / 600) % 2 == 0;
            String cursor = blink ? "|" : " ";
            String editText = display + cursor + group.title.substring(Math.min(cursorPos, group.title.length()));
            int tw = Minecraft.getMinecraft().fontRenderer.getStringWidth(editText);
            if (Math.round(tw * z) > maxTitleW) {
                int charW = Minecraft.getMinecraft().fontRenderer.getStringWidth("W");
                int maxChars = Math.max(1, (int) (maxTitleW / (charW * z)));
                if (editText.length() > maxChars) {
                    editText = editText.substring(editText.length() - maxChars);
                }
            }
            GuiDraw.drawText(editText, titleX, Math.round(4 * z), z, PlannhColors.TEXT_WHITE.getColor(), false);
        } else {
            String displayTitle = group.title;
            int titleW = Minecraft.getMinecraft().fontRenderer.getStringWidth(displayTitle);
            if (Math.round(titleW * z) > maxTitleW) {
                while (!displayTitle.isEmpty()
                    && Math.round(Minecraft.getMinecraft().fontRenderer.getStringWidth(displayTitle) * z) > maxTitleW) {
                    displayTitle = displayTitle.substring(0, displayTitle.length() - 1);
                }
                displayTitle += "\u2026";
            }
            GuiDraw.drawText(displayTitle, titleX, Math.round(4 * z), z, PlannhColors.TEXT_WHITE.getColor(), false);

            // Node count badge
            int badgeX = titleX + Math.round(Minecraft.getMinecraft().fontRenderer.getStringWidth(displayTitle) * z)
                + Math.round(6 * z);
            String badge = "(" + group.nodeIds.size() + ")";
            GuiDraw.drawText(badge, badgeX, Math.round(4 * z), z * 0.8f, PlannhColors.TEXT_MUTED.getColor(), false);
        }

        // Color swatch
        int swatchX = pw - Math.round(CLOSE_W * z) - swatchW - swatchGap - Math.max(1, Math.round(z));
        int swatchY = Math.round(4 * z);
        GuiDraw.drawRect(swatchX, swatchY, swatchW, Math.round(10 * z), titleCol);

        // Close button
        int cx = pw - Math.round(CLOSE_W * z) - Math.max(1, Math.round(z));
        int cy = Math.max(1, Math.round(z));
        int cs = Math.round(CLOSE_W * z);
        GuiDraw.drawRect(cx, cy, cs, cs, PlannhColors.NOTE_CLOSE_BG.getColor());
        int xw = Minecraft.getMinecraft().fontRenderer.getStringWidth("x");
        int txtX = cx + (cs - xw) / 2;
        GuiDraw.drawText("x", txtX, cy + Math.round(z), 1.0f, PlannhColors.TEXT_WHITE.getColor(), false);

        // Resize cursor indicator
        if (!group.collapsed) {
            int absMX = getContext().getAbsMouseX();
            int absMY = getContext().getAbsMouseY();
            int lmx = absMX - getArea().x;
            int lmy = absMY - getArea().y;
            ResizeMode rm = getResizeModeAt(lmx, lmy);
            if (rm != ResizeMode.NONE) {
                String sym = getResizeCursorSymbol(rm);
                int sx = lmx + Math.round(10 * z);
                int sy = lmy + Math.round(10 * z);
                GuiDraw.drawText(sym, sx, sy, z * 1.2f, titleCol, false);
            }
        }
    }

    @Override
    public Result onMousePressed(int mouseButton) {
        if (mouseButton != 0) return Result.IGNORE;
        float z = canvas.getZoom();
        int mx = getContext().getMouseX();
        int my = getContext().getMouseY();
        int pw = getArea().width;
        int ph = getArea().height;
        int hh = Math.round(HEADER_H * z);

        // Close button
        int cx = pw - Math.round(CLOSE_W * z) - Math.max(1, Math.round(z));
        int cy = Math.max(1, Math.round(z));
        int cs = Math.round(CLOSE_W * z);
        if (mx >= cx && mx < cx + cs && my >= cy && my < cy + cs) {
            canvas.removeGroup(group.id);
            return Result.SUCCESS;
        }

        // Color swatch
        int swatchW = Math.round(SWATCH_W * z);
        int swatchGap = Math.round(4 * z);
        int swatchX = pw - Math.round(CLOSE_W * z) - swatchW - swatchGap - Math.max(1, Math.round(z));
        int swatchY = Math.round(4 * z);
        if (mx >= swatchX && mx < swatchX + swatchW && my >= swatchY && my < swatchY + Math.round(10 * z)) {
            int currentColor = group.colorOverride != 0 ? group.colorOverride : titleColor();
            ColorPickerDialog picker = new ColorPickerDialog(chosenColor -> {
                group.colorOverride = chosenColor;
                canvas.rebuildGroupWidgets();
            }, currentColor, false);
            IPanelHandler.simple(getPanel(), (parent, player) -> picker, true)
                .openPanel();
            return Result.SUCCESS;
        }

        // Collapse toggle
        if (my < hh && mx < Math.round(COLLAPSE_W * z)) {
            group.collapsed = !group.collapsed;
            float z2 = canvas.getZoom();
            size(Math.round(group.width * z2), computePixelHeight(z2));
            if (group.collapsed) {
                canvas.hideGroupNodes(group.id);
            } else {
                canvas.showGroupNodes(group.id);
            }
            return Result.SUCCESS;
        }

        // Resize handles (after close/swatch/collapse so buttons take priority)
        if (!group.collapsed) {
            ResizeMode rm = getResizeModeAt(mx, my);
            if (rm != ResizeMode.NONE) {
                resizeMode = rm;
                resizeStartMX = getContext().getAbsMouseX();
                resizeStartMY = getContext().getAbsMouseY();
                resizeStartX = group.x;
                resizeStartY = group.y;
                resizeStartW = group.width;
                resizeStartH = group.height;
                return Result.SUCCESS;
            }
        }

        // Double-click on title → edit
        if (my < hh) {
            long now = Minecraft.getSystemTime();
            if (now - lastClickTime < 300) {
                lastClickTime = 0;
                canvas.startEditingGroup(group.id);
                return Result.SUCCESS;
            }
            lastClickTime = now;

            // Title bar drag
            dragging = true;
            dragStartMX = getContext().getAbsMouseX();
            dragStartMY = getContext().getAbsMouseY();
            groupStartX = group.x;
            groupStartY = group.y;
            return Result.SUCCESS;
        }

        return Result.IGNORE;
    }

    private ResizeMode getResizeModeAt(int mx, int my) {
        float z = canvas.getZoom();
        int rm = Math.max(1, Math.round(RESIZE_MARGIN * z));
        int pw = getArea().width;
        int ph = getArea().height;

        boolean top = my < rm;
        boolean bottom = my > ph - rm;
        boolean left = mx < rm;
        boolean right = mx > pw - rm;

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

    private static String getResizeCursorSymbol(ResizeMode mode) {
        switch (mode) {
            case TL:
                return "\u2196";
            case TR:
                return "\u2197";
            case BR:
                return "\u2198";
            case BL:
                return "\u2199";
            case T:
            case B:
                return "\u2195";
            case L:
            case R:
                return "\u2194";
            default:
                return "";
        }
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        if (resizeMode != ResizeMode.NONE) {
            resizeMode = ResizeMode.NONE;
            canvas.onGroupResizeFinished();
            return true;
        }
        if (dragging) {
            dragging = false;
            canvas.onGroupDragFinished();
        }
        return true;
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        if (resizeMode != ResizeMode.NONE && mouseButton == 0) {
            int dx = getContext().getAbsMouseX() - resizeStartMX;
            int dy = getContext().getAbsMouseY() - resizeStartMY;
            float z = canvas.getZoom();
            int dgx = Math.round(dx / z);
            int dgy = Math.round(dy / z);

            int newX = resizeStartX, newY = resizeStartY;
            int newW = resizeStartW, newH = resizeStartH;

            switch (resizeMode) {
                case TL:
                    newX = resizeStartX + dgx;
                    newY = resizeStartY + dgy;
                    newW = resizeStartW - dgx;
                    newH = resizeStartH - dgy;
                    break;
                case T:
                    newY = resizeStartY + dgy;
                    newH = resizeStartH - dgy;
                    break;
                case TR:
                    newY = resizeStartY + dgy;
                    newW = resizeStartW + dgx;
                    newH = resizeStartH - dgy;
                    break;
                case R:
                    newW = resizeStartW + dgx;
                    break;
                case BR:
                    newW = resizeStartW + dgx;
                    newH = resizeStartH + dgy;
                    break;
                case B:
                    newH = resizeStartH + dgy;
                    break;
                case BL:
                    newX = resizeStartX + dgx;
                    newW = resizeStartW - dgx;
                    newH = resizeStartH + dgy;
                    break;
                case L:
                    newX = resizeStartX + dgx;
                    newW = resizeStartW - dgx;
                    break;
                default:
                    break;
            }

            if (newW < MIN_W) {
                if (newX != resizeStartX) newX = resizeStartX + resizeStartW - MIN_W;
                newW = MIN_W;
            }
            if (newH < MIN_H) {
                if (newY != resizeStartY) newY = resizeStartY + resizeStartH - MIN_H;
                newH = MIN_H;
            }

            group.x = newX;
            group.y = newY;
            group.width = newW;
            group.height = newH;
            syncTransform(z, canvas.getPanX(), canvas.getPanY());
            return;
        }

        if (dragging && mouseButton == 0) {
            int dx = getContext().getAbsMouseX() - dragStartMX;
            int dy = getContext().getAbsMouseY() - dragStartMY;
            float z = canvas.getZoom();
            int newX = groupStartX + Math.round(dx / z);
            int newY = groupStartY + Math.round(dy / z);
            int deltaX = newX - group.x;
            int deltaY = newY - group.y;
            group.x = newX;
            group.y = newY;
            canvas.moveGroupNodes(group.id, deltaX, deltaY);
            syncTransform(z, canvas.getPanX(), canvas.getPanY());
        }
    }

    @Override
    public boolean onMouseScroll(UpOrDown direction, int amount) {
        return false;
    }
}
