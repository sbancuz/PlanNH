package com.sbancuz.plannh.gui;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.textfield.BaseTextFieldWidget;

public class NoteTextWidget extends BaseTextFieldWidget<NoteTextWidget> implements IDraggable {

    private final NoteWidget2 parent;
    private final GuiHelper.DoubleClickDetector doubleClick = new GuiHelper.DoubleClickDetector();

    public NoteTextWidget(NoteWidget2 parent, List<String> baseText) {
        this.parent = parent;
        handler.getText()
            .addAll(baseText);
        handler.setMaxLines(1000);
        setTextAlignment(Alignment.TopLeft);
        background();
        hoverBackground();
        setTextColor(PlannhColors.TEXT_NOTE.getColor());
        paddingTop(4);
        resize();
    }

    @Override
    protected void onTextChanged() {
        super.onTextChanged();
        resize();
    }

    private void resize() {
        size(
            Math.max((int) renderer.getLastActualWidth() + 20, NoteWidget2.NOTE_W),
            Math.max((int) renderer.getLastActualHeight() + 5, NoteWidget2.NOTE_H));
    }

    public List<String> getText() {
        return handler.getText();
    }

    @Override
    public boolean canScrollHorizontally() {
        return true;
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (!doubleClick.check() && !isFocused()) return Result.IGNORE;
        return super.onMousePressed(mouseButton);
    }

    @Override
    public void onFocus(ModularGuiContext context) {
        super.onFocus(context);
        parent.setEditing(true);
    }

    @Override
    public void onRemoveFocus(ModularGuiContext context) {
        super.onRemoveFocus(context);
        parent.setEditing(false);
    }

    @Override
    public void drawMovingState(ModularGuiContext modularGuiContext, float v) {
        parent.drawMovingState(modularGuiContext, v);
    }

    @Override
    public boolean onDragStart(int i) {
        return parent.onDragStart(i);
    }

    @Override
    public void onDragEnd(boolean b) {
        parent.onDragEnd(b);
    }

    @Override
    public void onDrag(int i, long l) {
        parent.onDrag(i, l);
    }

    @Override
    public @Nullable Area getMovingArea() {
        return parent.getMovingArea();
    }

    @Override
    public boolean isMoving() {
        return parent.isMoving();
    }

    @Override
    public void setMoving(boolean b) {
        parent.setMoving(b);
    }
}
