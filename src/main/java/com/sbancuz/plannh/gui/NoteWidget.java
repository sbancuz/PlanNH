package com.sbancuz.plannh.gui;

import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.data.flowchart.Note;

import lombok.Getter;

public class NoteWidget extends Widget<NoteWidget> implements Interactable {

    private static final int NOTE_W = 140;
    private static final int NOTE_H = 60;
    private static final int CLOSE_W = 12;

    @Getter
    private final Note note;
    private final CanvasWidget canvas;

    private boolean dragging = false;
    private int dragStartMX, dragStartMY;
    private int noteStartX, noteStartY;
    private final GuiHelper.DoubleClickDetector doubleClick = new GuiHelper.DoubleClickDetector();

    @Getter
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

    private int zq(float v) {
        return GuiHelper.zq(v, canvas.getZoom());
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

        int bg = editing ? PlannhColors.NOTE_BG_EDITING.getColor() : PlannhColors.NOTE_BG.getColor();
        int border = editing ? PlannhColors.NOTE_BORDER_EDIT.getColor() : PlannhColors.NOTE_BORDER.getColor();

        GuiDraw.drawRect(0, 0, w, h, bg);
        GuiHelper.drawRectBorder(0, 0, w, h, 1, border);
        GuiHelper.drawCloseButton(
            z,
            w,
            CLOSE_W,
            0,
            PlannhColors.NOTE_CLOSE_BG.getColor(),
            PlannhColors.TEXT_WHITE.getColor());

        // Text
        int pad = zq(4);
        if (editing) {
            String display = note.text.substring(0, Math.min(cursorPos, note.text.length()));
            boolean blink = (Minecraft.getSystemTime() / 600) % 2 == 0;
            String cursor = blink ? "|" : " ";
            GuiDraw.drawText(
                display + cursor + note.text.substring(Math.min(cursorPos, note.text.length())),
                pad,
                zq(8),
                z * 0.9f,
                PlannhColors.TEXT_DARK.getColor(),
                false);
        } else {
            GuiDraw.drawText(
                note.text.isEmpty() ? "Note" : note.text,
                pad,
                zq(8),
                z * 0.9f,
                PlannhColors.TEXT_NOTE.getColor(),
                false);
        }
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (mouseButton != 0) return Result.IGNORE;
        int mx = getContext().getMouseX();
        int my = getContext().getMouseY();

        if (GuiHelper.isInsideCloseButton(mx, my, canvas.getZoom(), getArea().width, CLOSE_W, 0)) {
            canvas.removeNote(note.id);
            return Result.SUCCESS;
        }

        if (doubleClick.check()) {
            canvas.startEditingNote(note.id);
            return Result.SUCCESS;
        }

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
}
