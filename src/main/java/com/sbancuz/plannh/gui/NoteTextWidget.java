package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.sbancuz.plannh.data.flowchart.Note;

public class NoteTextWidget extends FlowchartTextFieldWidget {

    protected static final int NOTE_MIN_W = 140;
    protected static final int NOTE_MIN_H = 60;

    public NoteTextWidget(NoteWidget parent) {
        super(parent);
        background(
            new DynamicDrawable(
                () -> new Rectangle()
                    .color((isEditing ? PlannhColors.NOTE_BG_EDITING : PlannhColors.NOTE_BG).getColor())),
            new DynamicDrawable(
                () -> new Rectangle().hollow()
                    .color((isEditing ? PlannhColors.NOTE_BORDER_EDIT : PlannhColors.NOTE_BORDER).getColor())));

        Note note = parent.getData();
        // add loaded note text if not empty, initialize it otherwise
        if (note.getText()
            .isEmpty())
            handler.getText()
                .add("");
        else handler.getText()
            .addAll(note.getText());

        // set note text to handler through reference
        note.setText(handler.getText());

        handler.setMaxLines(1000);
        setTextAlignment(Alignment.TopLeft);
        setTextColor(PlannhColors.TEXT_NOTE.getColor());
        paddingTop(4);

        resize(
            renderer.getMaxWidth(handler.getText()),
            (int) renderer.getFontHeight() * handler.getText()
                .size());
    }

    @Override
    protected void onTextChanged() {
        super.onTextChanged();
        resize((int) renderer.getLastActualWidth(), (int) renderer.getLastActualHeight());
    }

    protected void resize(int w, int h) {
        size(Math.max(w + 5, NOTE_MIN_W), Math.max(h + 5, NOTE_MIN_H));
    }
}
