package com.sbancuz.plannh.gui;

import java.util.Collections;

import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;

import lombok.Getter;
import lombok.Setter;

public class NoteWidget2 extends FlowchartWidget<NoteWidget2> {

    protected static final int NOTE_W = 140;
    protected static final int NOTE_H = 60;

    @Getter
    @Setter
    private String note = "";
    @Getter
    @Setter
    private boolean isEditing = false;
    private final NoteTextWidget text;

    protected NoteWidget2(CanvasWidget canvas) {
        super(canvas);

        coverChildren(NOTE_W, NOTE_H);
        background(
            new DynamicDrawable(
                () -> new Rectangle()
                    .color((isEditing ? PlannhColors.NOTE_BG_EDITING : PlannhColors.NOTE_BG).getColor())),
            new DynamicDrawable(
                () -> new Rectangle().hollow()
                    .color((isEditing ? PlannhColors.NOTE_BORDER_EDIT : PlannhColors.NOTE_BORDER).getColor())));
        text = new NoteTextWidget(this, Collections.singletonList(""));
        child(text);
        child(
            new CloseButtonWidget(this, canvas).topRel(0)
                .rightRel(0));
    }
}
