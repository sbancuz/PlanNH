package com.sbancuz.plannh.gui;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.data.flowchart.Note;

public class NoteWidget extends Widget<NoteWidget> implements Interactable {

    private static final int NOTE_W = 140;
    private static final int NOTE_H = 60;
    private static final int CLOSE_W = 10;

    private final Note note;
    private final CanvasWidget canvas;

    public Note getNote() {
        return note;
    }

    private boolean dragging = false;
    private int dragStartMX, dragStartMY;
    private int noteStartX, noteStartY;
    private long lastClickTime = 0;

    private boolean editing = false;
    private int cursorPos = 0;

    public NoteWidget(Note note, CanvasWidget canvas) {
        this.note = note;
        this.canvas = canvas;
        float z = canvas.getZoom();
        size(Math.round(NOTE_W * z), Math.round(NOTE_H * z));
    }

    public void syncTransform(float zoom, float panX, float panY) {
        int sx = Math.round(note.x * zoom + panX);
        int sy = Math.round(note.y * zoom + panY);
        pos(sx, sy);
        size(Math.round(NOTE_W * zoom), Math.round(NOTE_H * zoom));
    }

    public boolean isEditing() {
        return editing;
    }

    public void setEditing(boolean editing) {
        this.editing = editing;
        this.cursorPos = note.text.length();
        if (!editing) {
            canvas.onNoteEditingDone();
        }
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        float z = canvas.getZoom();
        int w = getArea().width;
        int h = getArea().height;

        int bg = editing ? Color.argb(230, 255, 250, 200) : Color.argb(200, 255, 240, 160);
        int border = editing ? Color.argb(200, 180, 160, 60) : Color.argb(160, 180, 160, 80);

        GuiDraw.drawRect(0, 0, w, h, bg);
        GuiDraw.drawRect(0, 0, w, 1, border);
        GuiDraw.drawRect(0, h - 1, w, 1, border);
        GuiDraw.drawRect(0, 0, 1, h, border);
        GuiDraw.drawRect(w - 1, 0, 1, h, border);

        // Close button
        int cx = w - Math.round(CLOSE_W * z);
        GuiDraw.drawRect(cx, 0, Math.round(CLOSE_W * z), Math.round(CLOSE_W * z), Color.argb(180, 200, 60, 60));
        int cw = Minecraft.getMinecraft().fontRenderer.getStringWidth("x");
        int txtX = cx + (Math.round(CLOSE_W * z) - cw) / 2;
        GuiDraw.drawText("x", txtX, 2, 1.0f, 0xFFFFFF, false);

        // Text
        int pad = Math.round(4 * z);
        if (editing) {
            String display = note.text.substring(0, Math.min(cursorPos, note.text.length()));
            boolean blink = (System.currentTimeMillis() / 600) % 2 == 0;
            String cursor = blink ? "|" : " ";
            GuiDraw.drawText(
                display + cursor + note.text.substring(Math.min(cursorPos, note.text.length())),
                pad,
                Math.round(8 * z),
                z * 0.9f,
                0x444444,
                false);
        } else {
            GuiDraw
                .drawText(note.text.isEmpty() ? "Note" : note.text, pad, Math.round(8 * z), z * 0.9f, 0x555555, false);
        }
    }

    @Override
    public Result onMousePressed(int mouseButton) {
        if (mouseButton != 0) return Result.IGNORE;
        int mx = getContext().getMouseX();
        int my = getContext().getMouseY();

        int cx = getArea().width - Math.round(CLOSE_W * canvas.getZoom());
        if (mx >= cx && my < Math.round(CLOSE_W * canvas.getZoom())) {
            canvas.removeNote(note.id);
            return Result.SUCCESS;
        }

        long now = Minecraft.getSystemTime();
        if (now - lastClickTime < 300) {
            lastClickTime = 0;
            canvas.startEditingNote(note.id);
            return Result.SUCCESS;
        }
        lastClickTime = now;

        dragging = true;
        dragStartMX = getContext().getAbsMouseX();
        dragStartMY = getContext().getAbsMouseY();
        noteStartX = note.x;
        noteStartY = note.y;
        return Result.SUCCESS;
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        dragging = false;
        return true;
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        if (dragging && mouseButton == 0) {
            int dx = getContext().getAbsMouseX() - dragStartMX;
            int dy = getContext().getAbsMouseY() - dragStartMY;
            float z = canvas.getZoom();
            note.x = noteStartX + Math.round(dx / z);
            note.y = noteStartY + Math.round(dy / z);
            syncTransform(z, canvas.getPanX(), canvas.getPanY());
        }
    }

    @Override
    public boolean onMouseScroll(UpOrDown direction, int amount) {
        return false;
    }
}
